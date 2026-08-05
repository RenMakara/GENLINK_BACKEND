package kh.edu.acleda.campuslink.modules.media.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata for one stored object. The bytes live in object storage; this row is
 * the record of truth for ownership, integrity, and lifecycle.
 *
 * <p>Deliberately does not extend BaseEntity: media has its own lifecycle
 * timestamps ({@code created_at}, {@code uploaded_at}, {@code confirmed_at},
 * {@code deleted_at}) and does not need the created-by/updated-by audit columns.
 */
@Getter
@Setter
@Entity
@Table(name = "media_files")
public class MediaFile {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    /** Object key inside the bucket, e.g. {@code posts/2026/07/<uuid>.jpg}. */
    @Column(name = "storage_key", nullable = false, unique = true, columnDefinition = "TEXT")
    private String storageKey;

    /** CDN URL for PUBLIC media; null for PRIVATE. */
    @Column(name = "public_url", columnDefinition = "TEXT")
    private String publicUrl;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Lowercase hex SHA-256, computed client-side. Integrity + dedup. */
    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    /** Video/audio length in milliseconds; null otherwise. */
    @Column(name = "duration_ms")
    private Long durationMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 40)
    private MediaPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private MediaVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MediaStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Object's LastModified, read from the confirm-time HEAD. */
    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /** Soft delete. When set, the object is pending purge by the cleanup job. */
    @Column(name = "deleted_at")
    private Instant deletedAt;
}
