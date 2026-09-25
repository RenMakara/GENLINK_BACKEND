package kh.edu.acleda.campuslink.modules.admin.audit.dto.request;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.audit.enums.AuditStatus;
import kh.edu.acleda.campuslink.shared.exception.ApiException;

/** Validated filters shared by the audit list and export endpoints. */
public record AdminAuditLogCriteria(
    UUID adminUserId,
    String action,
    String targetType,
    LocalDate from,
    LocalDate to,
    AuditStatus status,
    int page,
    int size
) {

    private static final int MAX_ACTION_LENGTH = 80;
    private static final int MAX_TARGET_TYPE_LENGTH = 40;

    public static AdminAuditLogCriteria fromRequest(
        String adminId,
        String action,
        String targetType,
        LocalDate from,
        LocalDate to,
        String status,
        int page,
        int size) {
        validatePage(page, size);
        if (from != null && to != null && from.isAfter(to)) {
            throw invalid("from must be on or before to");
        }
        return new AdminAuditLogCriteria(
            parseUuid(adminId),
            normalize(action, "action", MAX_ACTION_LENGTH),
            normalize(targetType, "targetType", MAX_TARGET_TYPE_LENGTH),
            from,
            to,
            parseStatus(status),
            page,
            size);
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException exception) {
            throw invalid("adminId must be a valid UUID");
        }
    }

    private static AuditStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AuditStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid("Unsupported status value: " + raw.trim());
        }
    }

    private static String normalize(String raw, String parameter, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.length() > maxLength) {
            throw invalid(parameter + " must contain at most " + maxLength + " characters");
        }
        return value;
    }

    private static void validatePage(int page, int size) {
        if (page < 0) {
            throw invalid("page must be zero or greater");
        }
        if (size < 1 || size > 100) {
            throw invalid("size must be between 1 and 100");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(message, "VALIDATION_ERROR");
    }
}
