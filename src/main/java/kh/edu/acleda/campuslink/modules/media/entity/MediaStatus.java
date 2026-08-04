package kh.edu.acleda.campuslink.modules.media.entity;

/**
 * Upload state of a media record.
 *
 * <p>Deletion is not a status — it is the {@code deleted_at} timestamp (soft
 * delete). A row is PENDING between presign and confirm, then CONFIRMED.
 */
public enum MediaStatus {
    PENDING,
    CONFIRMED
}
