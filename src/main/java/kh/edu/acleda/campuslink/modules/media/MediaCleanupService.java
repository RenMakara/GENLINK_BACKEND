package kh.edu.acleda.campuslink.modules.media;

import kh.edu.acleda.campuslink.modules.media.entity.MediaFile;
import kh.edu.acleda.campuslink.modules.media.entity.MediaStatus;
import kh.edu.acleda.campuslink.modules.media.repository.MediaFileRepository;
import kh.edu.acleda.campuslink.modules.media.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Reclaims storage that nothing references any more. */
@Slf4j
@Service
public class MediaCleanupService {

    private final MediaFileRepository repository;
    private final StorageService storage;
    private final MediaCleanupProperties properties;

    public MediaCleanupService(MediaFileRepository repository,
                               StorageService storage,
                               MediaCleanupProperties properties) {
        this.repository = repository;
        this.storage = storage;
        this.properties = properties;
    }

    /** Outcome of one sweep, for structured logging and future metrics. */
    public record CleanupResult(int deleted, int failed, long durationMs, boolean batchFull) {
        static CleanupResult empty(long durationMs) {
            return new CleanupResult(0, 0, durationMs, false);
        }

        public int examined() {
            return deleted + failed;
        }
    }

    /** Remove rows for uploads that were presigned but never confirmed. */
    @Transactional
    public CleanupResult purgeOrphans() {
        long startedAt = System.nanoTime();
        Instant cutoff = Instant.now().minus(properties.getOrphanAge());

        List<MediaFile> candidates = repository.findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
            MediaStatus.PENDING, cutoff, PageRequest.of(0, properties.getBatchSize()));

        return sweep(candidates, startedAt);
    }

    /**
     * Delete the bytes of media soft-deleted longer ago than the retention window,
     * then drop the row.
     */
    @Transactional
    public CleanupResult purgeSoftDeleted() {
        long startedAt = System.nanoTime();
        Instant cutoff = Instant.now().minus(properties.getRetention());

        List<MediaFile> candidates = repository.findByDeletedAtIsNotNullAndDeletedAtBeforeOrderByDeletedAtAsc(
            cutoff, PageRequest.of(0, properties.getBatchSize()));

        return sweep(candidates, startedAt);
    }

    /** Soft-delete everything a user owns. */
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

    /** Delete each candidate's object, then remove the rows that succeeded. */
    private CleanupResult sweep(List<MediaFile> candidates, long startedAt) {
        if (candidates.isEmpty()) {
            return CleanupResult.empty(elapsedMs(startedAt));
        }

        List<MediaFile> removable = new ArrayList<>(candidates.size());
        int failed = 0;
        for (MediaFile media : candidates) {
            if (deleteObjectQuietly(media)) {
                removable.add(media);
            } else {
                failed++;
            }
        }
        repository.deleteAll(removable);

        return new CleanupResult(removable.size(), failed, elapsedMs(startedAt),
            candidates.size() == properties.getBatchSize());
    }

    /** Delete one object, reporting whether the row may now be removed. */
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

    private long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }
}
