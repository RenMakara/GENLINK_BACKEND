package kh.edu.acleda.campuslink.modules.media.repository;

import kh.edu.acleda.campuslink.modules.media.entity.MediaFile;
import kh.edu.acleda.campuslink.modules.media.entity.MediaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MediaFileRepository extends JpaRepository<MediaFile, UUID> {

    /** Active (not soft-deleted) media by id. */
    Optional<MediaFile> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Per-owner duplicate detection: an existing confirmed object with the same
     * bytes. Used to short-circuit a re-upload of identical content.
     */
    Optional<MediaFile> findFirstByOwnerIdAndChecksumSha256AndStatusAndDeletedAtIsNull(
        UUID ownerId, String checksumSha256, MediaStatus status);

    /** Orphan cleanup (Phase 3): presigned but never confirmed. */
    List<MediaFile> findByStatusAndCreatedAtBefore(MediaStatus status, Instant cutoff);

    /** Purge job (Phase 3): soft-deleted past the grace period. */
    List<MediaFile> findByDeletedAtIsNotNullAndDeletedAtBefore(Instant cutoff);
}
