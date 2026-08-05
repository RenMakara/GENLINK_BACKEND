package kh.edu.acleda.campuslink.modules.media;

import kh.edu.acleda.campuslink.modules.media.entity.MediaFile;
import kh.edu.acleda.campuslink.modules.media.entity.MediaStatus;
import kh.edu.acleda.campuslink.modules.media.repository.MediaFileRepository;
import kh.edu.acleda.campuslink.modules.media.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reclaims storage that nothing references any more.
 *
 * <p>Two leaks exist without this. A presigned upload that is never confirmed
 * leaves a PENDING row (and possibly an object) that no feature will ever use.
 * And a soft delete only stamps {@code deleted_at} — the bytes stay in the bucket,
 * costing money forever. Both are swept here.
 *
 * <p>Deleting the object before the row is deliberate: if the object delete fails
 * the row survives and the next run retries it. The reverse order would drop the
 * only pointer to the object and leak it permanently.
 */
@Slf4j
@Service
public class MediaCleanupService {

    /**
     * How long a PENDING row may sit before it is considered abandoned. Comfortably
     * longer than the presign expiry (5 min), so a slow upload is never reaped
     * out from under a client that is still working.
     */
    private static final Duration ORPHAN_AGE = Duration.ofHours(24);

    /** Bounds each run's memory and transaction size; a backlog finishes tomorrow. */
    private static final int BATCH_LIMIT = 500;

    private final MediaFileRepository repository;
    private final StorageService storage;
    private final int graceDays;

    public MediaCleanupService(MediaFileRepository repository,
                               StorageService storage,
                               @Value("${app.media.soft-delete-grace-days:30}") int graceDays) {
        this.repository = repository;
        this.storage = storage;
        this.graceDays = graceDays;
    }

    /**
     * Remove rows for uploads that were presigned but never confirmed.
     *
     * <p>The object may or may not exist — the client may have abandoned the flow
     * before or after the PUT — so the delete is attempted either way and a missing
     * object is not an error.
     *
     * @return how many orphan rows were removed
     */
    @Transactional
    public int purgeOrphans() {
        Instant cutoff = Instant.now().minus(ORPHAN_AGE);
        List<MediaFile> orphans = repository.findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
            MediaStatus.PENDING, cutoff, PageRequest.of(0, BATCH_LIMIT));

        if (orphans.isEmpty()) {
            return 0;
        }

        List<MediaFile> removed = orphans.stream().filter(this::deleteObjectQuietly).toList();
        repository.deleteAll(removed);

        log.info("Removed {} orphaned media row(s) presigned before {}", removed.size(), cutoff);
        warnIfBatchFull(orphans.size(), "orphan");
        return removed.size();
    }

    /**
     * Delete the bytes of media that was soft-deleted longer ago than the grace
     * period, then drop the row.
     *
     * @return how many objects were purged
     */
    @Transactional
    public int purgeSoftDeleted() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(graceDays));
        List<MediaFile> expired = repository.findByDeletedAtIsNotNullAndDeletedAtBeforeOrderByDeletedAtAsc(
            cutoff, PageRequest.of(0, BATCH_LIMIT));

        if (expired.isEmpty()) {
            return 0;
        }

        List<MediaFile> removed = expired.stream().filter(this::deleteObjectQuietly).toList();
        repository.deleteAll(removed);

        log.info("Purged {} media object(s) soft-deleted before {}", removed.size(), cutoff);
        warnIfBatchFull(expired.size(), "purge");
        return removed.size();
    }

    /**
     * Soft-delete everything a user owns. Called when an account is anonymised, so
     * their uploads follow the same 30-day path as the account itself rather than
     * lingering in the bucket after the person is gone.
     *
     * @return how many media rows were soft-deleted
     */
    @Transactional
    public int softDeleteAllOwnedBy(UUID ownerId) {
        List<MediaFile> owned = repository.findByOwnerIdAndDeletedAtIsNull(ownerId);
        if (owned.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        owned.forEach(media -> media.setDeletedAt(now));
        repository.saveAll(owned);
        log.debug("Soft-deleted {} media row(s) owned by {}", owned.size(), ownerId);
        return owned.size();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Delete one object, reporting whether the row may now be removed.
     *
     * <p>A failure here (network, permissions) is logged and the row is kept, so
     * the next run retries instead of orphaning the bytes with no record of them.
     */
    private boolean deleteObjectQuietly(MediaFile media) {
        try {
            storage.delete(media.getStorageKey());
            return true;
        } catch (Exception ex) {
            log.warn("Could not delete object {} — keeping the row so the next run retries: {}",
                media.getStorageKey(), ex.getMessage());
            return false;
        }
    }

    private void warnIfBatchFull(int found, String sweep) {
        if (found == BATCH_LIMIT) {
            log.warn("Media {} sweep hit the {}-row batch limit; more remain for the next run",
                sweep, BATCH_LIMIT);
        }
    }
}
