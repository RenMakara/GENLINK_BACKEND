package kh.edu.acleda.campuslink.modules.media;

import kh.edu.acleda.campuslink.modules.media.entity.MediaFile;
import kh.edu.acleda.campuslink.modules.media.entity.MediaPurpose;
import kh.edu.acleda.campuslink.modules.media.entity.MediaStatus;
import kh.edu.acleda.campuslink.modules.media.entity.MediaVisibility;
import kh.edu.acleda.campuslink.modules.media.repository.MediaFileRepository;
import kh.edu.acleda.campuslink.modules.media.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaCleanupServiceTest {

    @Mock private MediaFileRepository repository;
    @Mock private StorageService storage;

    private MediaCleanupService service;

    private static final int GRACE_DAYS = 30;
    private static final int BATCH_SIZE = 500;

    @BeforeEach
    void setUp() {
        MediaCleanupProperties properties = new MediaCleanupProperties();
        properties.setOrphanAge(Duration.ofHours(24));
        properties.setRetention(Duration.ofDays(GRACE_DAYS));
        properties.setBatchSize(BATCH_SIZE);
        service = new MediaCleanupService(repository, storage, properties);
    }

    // ── orphans ───────────────────────────────────────────────────────────

    @Test
    void purgeOrphansDeletesObjectAndRowForUnconfirmedUploads() {
        MediaFile orphan = media(MediaStatus.PENDING);
        when(repository.findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
            eq(MediaStatus.PENDING), any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of(orphan));

        assertThat(service.purgeOrphans().deleted()).isEqualTo(1);

        verify(storage).delete(orphan.getStorageKey());
        verify(repository).deleteAll(List.of(orphan));
    }

    @Test
    void purgeOrphansOnlyLooksAtRowsOlderThan24h() {
        when(repository.findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
            eq(MediaStatus.PENDING), any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of());

        service.purgeOrphans();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
            eq(MediaStatus.PENDING), cutoff.capture(), any(Pageable.class));
        assertThat(cutoff.getValue())
            .isCloseTo(Instant.now().minus(24, ChronoUnit.HOURS), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void purgeOrphansWritesNothingWhenThereAreNone() {
        when(repository.findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
            eq(MediaStatus.PENDING), any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of());

        assertThat(service.purgeOrphans().deleted()).isZero();

        verify(repository, never()).deleteAll(anyList());
        verify(storage, never()).delete(anyString());
    }

    // ── soft-delete purge ─────────────────────────────────────────────────

    @Test
    void purgeSoftDeletedRemovesBytesThenRow() {
        MediaFile expired = media(MediaStatus.CONFIRMED);
        expired.setDeletedAt(Instant.now().minus(GRACE_DAYS + 5L, ChronoUnit.DAYS));
        when(repository.findByDeletedAtIsNotNullAndDeletedAtBeforeOrderByDeletedAtAsc(
            any(Instant.class), any(Pageable.class))).thenReturn(List.of(expired));

        assertThat(service.purgeSoftDeleted().deleted()).isEqualTo(1);

        verify(storage).delete(expired.getStorageKey());
        verify(repository).deleteAll(List.of(expired));
    }

    @Test
    void purgeSoftDeletedUsesTheConfiguredGracePeriod() {
        when(repository.findByDeletedAtIsNotNullAndDeletedAtBeforeOrderByDeletedAtAsc(
            any(Instant.class), any(Pageable.class))).thenReturn(List.of());

        service.purgeSoftDeleted();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).findByDeletedAtIsNotNullAndDeletedAtBeforeOrderByDeletedAtAsc(
            cutoff.capture(), any(Pageable.class));
        assertThat(cutoff.getValue())
            .isCloseTo(Instant.now().minus(GRACE_DAYS, ChronoUnit.DAYS), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void keepsTheRowWhenTheObjectDeleteFailsSoTheNextRunRetries() {
        // Dropping the row here would leave the bytes in the bucket with nothing
        // pointing at them — an unrecoverable leak.
        MediaFile expired = media(MediaStatus.CONFIRMED);
        expired.setDeletedAt(Instant.now().minus(GRACE_DAYS + 1L, ChronoUnit.DAYS));
        when(repository.findByDeletedAtIsNotNullAndDeletedAtBeforeOrderByDeletedAtAsc(
            any(Instant.class), any(Pageable.class))).thenReturn(List.of(expired));
        doThrow(new RuntimeException("network")).when(storage).delete(expired.getStorageKey());

        assertThat(service.purgeSoftDeleted().deleted()).isZero();

        verify(repository).deleteAll(List.of()); // nothing removed
    }

    @Test
    void oneFailingObjectDoesNotStopTheRestOfTheBatch() {
        MediaFile bad = media(MediaStatus.CONFIRMED);
        MediaFile good = media(MediaStatus.CONFIRMED);
        when(repository.findByDeletedAtIsNotNullAndDeletedAtBeforeOrderByDeletedAtAsc(
            any(Instant.class), any(Pageable.class))).thenReturn(List.of(bad, good));
        doThrow(new RuntimeException("boom")).when(storage).delete(bad.getStorageKey());

        assertThat(service.purgeSoftDeleted().deleted()).isEqualTo(1);

        verify(repository).deleteAll(List.of(good));
    }

    // ── metrics ───────────────────────────────────────────────────────────

    @Test
    void resultReportsFailuresSeparatelyFromDeletions() {
        MediaFile bad = media(MediaStatus.CONFIRMED);
        MediaFile good = media(MediaStatus.CONFIRMED);
        when(repository.findByDeletedAtIsNotNullAndDeletedAtBeforeOrderByDeletedAtAsc(
            any(Instant.class), any(Pageable.class))).thenReturn(List.of(bad, good));
        doThrow(new RuntimeException("boom")).when(storage).delete(bad.getStorageKey());

        MediaCleanupService.CleanupResult result = service.purgeSoftDeleted();

        assertThat(result.deleted()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.examined()).isEqualTo(2);
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void resultFlagsWhenTheBatchCeilingWasHitSoMoreRemain() {
        // A full batch means the backlog is larger than one run can clear; the
        // job logs "+more" so a silent partial sweep is not mistaken for done.
        List<MediaFile> full = java.util.stream.IntStream.range(0, BATCH_SIZE)
            .mapToObj(i -> media(MediaStatus.PENDING)).toList();
        when(repository.findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
            eq(MediaStatus.PENDING), any(Instant.class), any(Pageable.class)))
            .thenReturn(full);

        assertThat(service.purgeOrphans().batchFull()).isTrue();
    }

    @Test
    void batchSizeIsTakenFromConfiguration() {
        when(repository.findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
            eq(MediaStatus.PENDING), any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of());

        service.purgeOrphans();

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
            eq(MediaStatus.PENDING), any(Instant.class), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(BATCH_SIZE);
    }

    @Test
    void runningTwiceIsIndistinguishableFromRunningOnce() {
        // Idempotency: the second pass selects nothing because the first removed
        // the rows, and object deletes are no-ops on an already-missing key.
        MediaFile orphan = media(MediaStatus.PENDING);
        when(repository.findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
            eq(MediaStatus.PENDING), any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of(orphan))
            .thenReturn(List.of());

        assertThat(service.purgeOrphans().deleted()).isEqualTo(1);
        MediaCleanupService.CleanupResult second = service.purgeOrphans();

        assertThat(second.deleted()).isZero();
        assertThat(second.failed()).isZero();
    }

    // ── account deletion ──────────────────────────────────────────────────

    @Test
    void softDeleteAllOwnedByStampsEveryRowWithoutTouchingStorage() {
        UUID ownerId = UUID.randomUUID();
        MediaFile a = media(MediaStatus.CONFIRMED);
        MediaFile b = media(MediaStatus.CONFIRMED);
        when(repository.findByOwnerIdAndDeletedAtIsNull(ownerId)).thenReturn(List.of(a, b));

        assertThat(service.softDeleteAllOwnedBy(ownerId)).isEqualTo(2);

        assertThat(a.getDeletedAt()).isNotNull();
        assertThat(b.getDeletedAt()).isNotNull();
        // Bytes are removed later by the purge sweep, not here — the grace period
        // has to run first.
        verify(storage, never()).delete(anyString());
        verify(repository).saveAll(anyList());
    }

    @Test
    void softDeleteAllOwnedByIsANoOpForAUserWithNoMedia() {
        UUID ownerId = UUID.randomUUID();
        when(repository.findByOwnerIdAndDeletedAtIsNull(ownerId)).thenReturn(List.of());

        assertThat(service.softDeleteAllOwnedBy(ownerId)).isZero();

        verify(repository, never()).saveAll(anyList());
    }

    // ── helper ────────────────────────────────────────────────────────────

    private MediaFile media(MediaStatus status) {
        MediaFile m = new MediaFile();
        ReflectionTestUtils.setField(m, "id", UUID.randomUUID());
        m.setOwnerId(UUID.randomUUID());
        m.setStorageKey("posts/2026/08/" + UUID.randomUUID() + ".jpg");
        m.setContentType("image/jpeg");
        m.setSizeBytes(1024L);
        m.setPurpose(MediaPurpose.POST);
        m.setVisibility(MediaVisibility.PUBLIC);
        m.setStatus(status);
        return m;
    }
}
