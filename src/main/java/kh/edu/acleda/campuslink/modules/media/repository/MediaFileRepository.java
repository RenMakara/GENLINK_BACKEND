package kh.edu.acleda.campuslink.modules.media.repository;

import kh.edu.acleda.campuslink.modules.media.entity.MediaFile;
import kh.edu.acleda.campuslink.modules.media.entity.MediaStatus;
import org.springframework.data.domain.Pageable;
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

    /**
     * Orphan cleanup: presigned but never confirmed.
     *
     * <p>Paged, like the anonymisation sweep — these run unattended, and an
     * unbounded query would be one backlog away from an OOM on a 768 MB container.
     */
    List<MediaFile> findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
        MediaStatus status, Instant cutoff, Pageable pageable);

    /** Purge job: soft-deleted past the grace period, oldest first. */
    List<MediaFile> findByDeletedAtIsNotNullAndDeletedAtBeforeOrderByDeletedAtAsc(
        Instant cutoff, Pageable pageable);

    /** Account deletion: every active media row owned by a user. */
    List<MediaFile> findByOwnerIdAndDeletedAtIsNull(UUID ownerId);
}
