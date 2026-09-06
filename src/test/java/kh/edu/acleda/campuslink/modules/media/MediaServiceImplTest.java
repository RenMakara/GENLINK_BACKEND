package kh.edu.acleda.campuslink.modules.media;

import kh.edu.acleda.campuslink.modules.media.dto.ConfirmRequest;
import kh.edu.acleda.campuslink.modules.media.dto.MediaResponse;
import kh.edu.acleda.campuslink.modules.media.dto.PresignRequest;
import kh.edu.acleda.campuslink.modules.media.dto.PresignResponse;
import kh.edu.acleda.campuslink.modules.media.entity.MediaFile;
import kh.edu.acleda.campuslink.modules.media.entity.MediaPurpose;
import kh.edu.acleda.campuslink.modules.media.entity.MediaStatus;
import kh.edu.acleda.campuslink.modules.media.entity.MediaVisibility;
import kh.edu.acleda.campuslink.modules.media.repository.MediaFileRepository;
import kh.edu.acleda.campuslink.modules.media.storage.StorageProperties;
import kh.edu.acleda.campuslink.modules.media.storage.StorageService;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceImplTest {

    @Mock private MediaFileRepository repository;
    @Mock private StorageService storage;
    @Mock private CurrentUserProvider currentUserProvider;

    private StorageProperties properties;
    private MediaServiceImpl service;

    private final UUID ownerId = UUID.randomUUID();
    private static final String CHECKSUM = "a".repeat(64);

    @BeforeEach
    void setUp() {
        properties = new StorageProperties();
        properties.setPresignExpirySeconds(300);
        properties.setMaxImageMb(5);
        properties.setMaxVideoMb(50);
        properties.setCdnBase("https://cdn.example.com");
        service = new MediaServiceImpl(repository, storage, properties, currentUserProvider);
    }

    // ── presign ───────────────────────────────────────────────────────────

    @Test
    void presignImageCreatesPendingRowAndReturnsUploadUrl() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(ownerId);
        when(repository.save(any(MediaFile.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        when(storage.presignUpload(anyString(), anyString(), isNull(), any(Duration.class)))
            .thenReturn(new StorageService.PresignedUpload(
                "https://signed.example.com/put", java.util.Map.of("Content-Type", "image/jpeg")));

        PresignResponse response = service.presignUpload(
            new PresignRequest("image/jpeg", 1_048_576, MediaPurpose.POST, CHECKSUM));

        assertThat(response.uploadUrl()).isEqualTo("https://signed.example.com/put");
        assertThat(response.storageKey()).startsWith("posts/").endsWith(".jpg");
        assertThat(response.expiresInSeconds()).isEqualTo(300);

        ArgumentCaptor<MediaFile> captor = ArgumentCaptor.forClass(MediaFile.class);
        verify(repository).save(captor.capture());
        MediaFile saved = captor.getValue();
        assertThat(saved.getOwnerId()).isEqualTo(ownerId);
        assertThat(saved.getStatus()).isEqualTo(MediaStatus.PENDING);
        assertThat(saved.getVisibility()).isEqualTo(MediaVisibility.PUBLIC);
        assertThat(saved.getChecksumSha256()).isEqualTo(CHECKSUM);
        assertThat(saved.getPublicUrl()).isNull(); // set only at confirm
    }

    @Test
    void presignRejectsVideoForAPurposeThatDisallowsIt() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(ownerId);

        assertThatThrownBy(() -> service.presignUpload(
            new PresignRequest("video/mp4", 1_000_000, MediaPurpose.POST, CHECKSUM)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).getErrorCode())
            .isEqualTo("MEDIA_VIDEO_NOT_ALLOWED");
        verify(repository, never()).save(any());
    }

    @Test
    void presignAllowsVideoForStory() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(ownerId);
        when(repository.save(any(MediaFile.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        when(storage.presignUpload(anyString(), anyString(), isNull(), any(Duration.class)))
            .thenReturn(new StorageService.PresignedUpload("https://signed", java.util.Map.of()));

        PresignResponse response = service.presignUpload(
            new PresignRequest("video/mp4", 10_000_000, MediaPurpose.STORY, CHECKSUM));

        assertThat(response.storageKey()).startsWith("stories/").endsWith(".mp4");
    }

    @Test
    void presignRejectsOversizeImage() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(ownerId);

        assertThatThrownBy(() -> service.presignUpload(
            new PresignRequest("image/png", 6L * 1024 * 1024, MediaPurpose.POST, CHECKSUM)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).getErrorCode())
            .isEqualTo("MEDIA_TOO_LARGE");
    }

    @Test
    void presignRejectsNonMediaContentType() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(ownerId);

        assertThatThrownBy(() -> service.presignUpload(
            new PresignRequest("application/pdf", 1000, MediaPurpose.POST, CHECKSUM)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).getErrorCode())
            .isEqualTo("MEDIA_INVALID_TYPE");
    }

    // ── confirm ───────────────────────────────────────────────────────────

    @Test
    void confirmVerifiesObjectAndMarksConfirmedWithPublicUrl() {
        MediaFile pending = pendingMedia();
        when(currentUserProvider.getCurrentUserId()).thenReturn(ownerId);
        when(repository.findByIdAndDeletedAtIsNull(pending.getId())).thenReturn(Optional.of(pending));
        Instant lastModified = Instant.now();
        when(storage.head(pending.getStorageKey()))
            .thenReturn(Optional.of(new StorageService.StoredObject(2048L, "image/jpeg", lastModified)));
        when(storage.publicUrl(pending.getStorageKey())).thenReturn("https://cdn.example.com/" + pending.getStorageKey());
        when(repository.save(any(MediaFile.class))).thenAnswer(inv -> inv.getArgument(0));

        MediaResponse response = service.confirmUpload(
            new ConfirmRequest(pending.getId(), 1080, 1350, null));

        assertThat(pending.getStatus()).isEqualTo(MediaStatus.CONFIRMED);
        assertThat(pending.getUploadedAt()).isEqualTo(lastModified);
        assertThat(pending.getSizeBytes()).isEqualTo(2048L); // actual from HEAD, not declared
        assertThat(pending.getWidth()).isEqualTo(1080);
        assertThat(pending.getConfirmedAt()).isNotNull();
        assertThat(response.url()).isEqualTo("https://cdn.example.com/" + pending.getStorageKey());
        verify(storage).makePublic(pending.getStorageKey()); // PUBLIC media access is configured by the storage adapter
    }

    @Test
    void confirmRejectsMediaOwnedByAnotherUserAsNotFound() {
        MediaFile pending = pendingMedia();
        when(currentUserProvider.getCurrentUserId()).thenReturn(UUID.randomUUID()); // different user
        when(repository.findByIdAndDeletedAtIsNull(pending.getId())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.confirmUpload(new ConfirmRequest(pending.getId(), null, null, null)))
            .isInstanceOf(NotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void confirmRejectsAlreadyConfirmed() {
        MediaFile media = pendingMedia();
        media.setStatus(MediaStatus.CONFIRMED);
        when(currentUserProvider.getCurrentUserId()).thenReturn(ownerId);
        when(repository.findByIdAndDeletedAtIsNull(media.getId())).thenReturn(Optional.of(media));

        assertThatThrownBy(() -> service.confirmUpload(new ConfirmRequest(media.getId(), null, null, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).getErrorCode())
            .isEqualTo("MEDIA_ALREADY_CONFIRMED");
    }

    @Test
    void confirmRejectsWhenObjectMissingInStorage() {
        MediaFile pending = pendingMedia();
        when(currentUserProvider.getCurrentUserId()).thenReturn(ownerId);
        when(repository.findByIdAndDeletedAtIsNull(pending.getId())).thenReturn(Optional.of(pending));
        when(storage.head(pending.getStorageKey())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmUpload(new ConfirmRequest(pending.getId(), null, null, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).getErrorCode())
            .isEqualTo("MEDIA_NOT_UPLOADED");
    }

    @Test
    void confirmRejectsEmptyObject() {
        MediaFile pending = pendingMedia();
        when(currentUserProvider.getCurrentUserId()).thenReturn(ownerId);
        when(repository.findByIdAndDeletedAtIsNull(pending.getId())).thenReturn(Optional.of(pending));
        when(storage.head(pending.getStorageKey()))
            .thenReturn(Optional.of(new StorageService.StoredObject(0L, "image/jpeg", Instant.now())));

        assertThatThrownBy(() -> service.confirmUpload(new ConfirmRequest(pending.getId(), null, null, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).getErrorCode())
            .isEqualTo("MEDIA_EMPTY");
        verify(repository, never()).save(any());
    }

    // ── resolveConfirmedUrl (Phase 2 integration primitive) ────────────────

    @Test
    void resolveConfirmedUrlReturnsCdnUrlForOwnedConfirmedPublicMedia() {
        MediaFile media = pendingMedia();
        media.setStatus(MediaStatus.CONFIRMED);
        media.setPublicUrl("https://cdn.example.com/" + media.getStorageKey());
        when(currentUserProvider.getCurrentUserId()).thenReturn(ownerId);
        when(repository.findByIdAndDeletedAtIsNull(media.getId())).thenReturn(Optional.of(media));

        assertThat(service.resolveConfirmedUrl(media.getId()))
            .isEqualTo("https://cdn.example.com/" + media.getStorageKey());
    }

    @Test
    void resolveConfirmedUrlRejectsMediaOwnedByAnotherUser() {
        MediaFile media = pendingMedia();
        media.setStatus(MediaStatus.CONFIRMED);
        when(currentUserProvider.getCurrentUserId()).thenReturn(UUID.randomUUID());
        when(repository.findByIdAndDeletedAtIsNull(media.getId())).thenReturn(Optional.of(media));

        assertThatThrownBy(() -> service.resolveConfirmedUrl(media.getId()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void resolveConfirmedUrlRejectsUnconfirmedMedia() {
        MediaFile media = pendingMedia(); // still PENDING
        when(currentUserProvider.getCurrentUserId()).thenReturn(ownerId);
        when(repository.findByIdAndDeletedAtIsNull(media.getId())).thenReturn(Optional.of(media));

        assertThatThrownBy(() -> service.resolveConfirmedUrl(media.getId()))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).getErrorCode())
            .isEqualTo("MEDIA_NOT_CONFIRMED");
    }

    // ── delete ────────────────────────────────────────────────────────────

    @Test
    void deleteSoftDeletesForOwner() {
        MediaFile media = pendingMedia();
        media.setStatus(MediaStatus.CONFIRMED);
        when(currentUserProvider.getCurrentUserId()).thenReturn(ownerId);
        when(repository.findByIdAndDeletedAtIsNull(media.getId())).thenReturn(Optional.of(media));
        when(repository.save(any(MediaFile.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(media.getId());

        assertThat(media.getDeletedAt()).isNotNull();
        verify(repository).save(media);
    }

    @Test
    void deleteRejectsNonOwnerAsNotFound() {
        MediaFile media = pendingMedia();
        when(currentUserProvider.getCurrentUserId()).thenReturn(UUID.randomUUID());
        when(repository.findByIdAndDeletedAtIsNull(media.getId())).thenReturn(Optional.of(media));

        assertThatThrownBy(() -> service.delete(media.getId()))
            .isInstanceOf(NotFoundException.class);
        verify(repository, never()).save(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private MediaFile withId(MediaFile m) {
        if (m.getId() == null) {
            ReflectionTestUtils.setField(m, "id", UUID.randomUUID());
        }
        return m;
    }

    private MediaFile pendingMedia() {
        MediaFile m = new MediaFile();
        ReflectionTestUtils.setField(m, "id", UUID.randomUUID());
        m.setOwnerId(ownerId);
        m.setStorageKey("posts/2026/07/" + UUID.randomUUID() + ".jpg");
        m.setContentType("image/jpeg");
        m.setSizeBytes(1024L);
        m.setChecksumSha256(CHECKSUM);
        m.setPurpose(MediaPurpose.POST);
        m.setVisibility(MediaVisibility.PUBLIC);
        m.setStatus(MediaStatus.PENDING);
        return m;
    }
}
