package kh.edu.acleda.campuslink.modules.admin.audit.enums;

/**
 * Whether the audited admin action actually succeeded.
 *
 * <p>{@link #FAILURE} exists because a failed privileged action is the one you most need on
 * record - an attempt to ban a user that errored, or a permission check that rejected. The
 * codebase has already been burned by losing exactly these: {@code SecurityAuditService} wrote
 * failures inside the caller's transaction, so they rolled back with the throw and were never
 * recorded. The admin audit writer uses {@code REQUIRES_NEW} for that reason.
 */
public enum AuditStatus {
    SUCCESS,
    FAILURE
}
