package kh.edu.acleda.campuslink.modules.admin.user;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.AdminErrorCode;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditEvent;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditService;
import kh.edu.acleda.campuslink.modules.admin.audit.enums.AuditStatus;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserListCriteria;
import kh.edu.acleda.campuslink.modules.admin.user.repository.AdminUserListRepository;
import kh.edu.acleda.campuslink.modules.auth.repository.RefreshTokenRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bounded, permission-aware streaming CSV export for the Admin user directory (ADM-087). */
@Service
public class AdminUserExportService {

    static final int MAX_EXPORT_ROWS = 50_000;
    static final int EXPORT_BATCH_SIZE = 1_000;

    private static final String ACTION_EXPORT = "USER_EXPORT";
    private static final String TARGET_TYPE_EXPORT = "USER_EXPORT";
    private static final String REDACTED = "[REDACTED]";
    private static final List<String> HEADERS = List.of(
        "userId",
        "fullName",
        "username",
        "email",
        "avatarUrl",
        "emailVerified",
        "accountStatus",
        "adminRoleName",
        "universityName",
        "workStatus",
        "level",
        "totalXp",
        "createdAt",
        "lastActiveAt",
        "phoneNumber",
        "dateOfBirth",
        "gender",
        "preferredLanguage",
        "authProvider");

    private final AdminUserListRepository adminUserListRepository;
    private final AdminUserRepository adminUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AdminAuditService adminAuditService;

    public AdminUserExportService(
        AdminUserListRepository adminUserListRepository,
        AdminUserRepository adminUserRepository,
        RefreshTokenRepository refreshTokenRepository,
        AdminAuditService adminAuditService) {
        this.adminUserListRepository = adminUserListRepository;
        this.adminUserRepository = adminUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.adminAuditService = adminAuditService;
    }

    /** Counts and audits the export before the HTTP response commits its CSV headers. */
    @Transactional
    public AdminUserExportPlan prepare(
        AdminUserListCriteria criteria,
        boolean piiIncluded,
        String reason) {
        if (criteria == null) {
            throw new ApiException("Export filters are required", "VALIDATION_ERROR");
        }
        String normalizedReason = requireReason(reason);
        long rowCount = adminUserListRepository.count(criteria);
        AdminUserExportPlan plan = new AdminUserExportPlan(criteria, rowCount, piiIncluded);
        if (rowCount > MAX_EXPORT_ROWS) {
            recordAudit(plan, normalizedReason, AuditStatus.FAILURE, AdminErrorCode.EXPORT_TOO_LARGE);
            throw new ApiException(
                "The filtered user list contains more than " + MAX_EXPORT_ROWS
                    + " rows. Narrow the filters and retry.",
                AdminErrorCode.EXPORT_TOO_LARGE);
        }

        recordAudit(plan, normalizedReason, AuditStatus.SUCCESS, null);
        return plan;
    }

    /** Writes the already-counted export in bounded database batches. */
    @Transactional(readOnly = true)
    public void writeCsv(AdminUserExportPlan plan, OutputStream output) throws IOException {
        if (plan == null || output == null) {
            throw new IllegalArgumentException("Export plan and output are required");
        }

        Writer writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
        writeRow(writer, HEADERS);

        int offset = 0;
        while (offset < plan.rowCount()) {
            List<User> users = adminUserListRepository.findBatch(
                plan.criteria(), offset, EXPORT_BATCH_SIZE);
            if (users.isEmpty()) {
                break;
            }
            writeUsers(writer, users, plan.piiIncluded());
            offset += users.size();
            writer.flush();
            if (users.size() < EXPORT_BATCH_SIZE) {
                break;
            }
        }
        writer.flush();
    }

    private void writeUsers(Writer writer, List<User> users, boolean piiIncluded) throws IOException {
        Collection<UUID> userIds = users.stream().map(User::getId).toList();
        Map<UUID, String> adminRoleNames = adminRoleNames(userIds);
        Map<UUID, Instant> lastActiveAt = lastActiveAt(userIds);

        for (User user : users) {
            writeRow(writer, List.of(
                value(user.getId()),
                value(user.getFullName()),
                value(user.getUsername()),
                value(user.getEmail()),
                value(user.getAvatarUrl()),
                value(user.isEmailVerified()),
                value(user.getAccountStatus()),
                value(adminRoleNames.get(user.getId())),
                value(user.getUniversityName()),
                value(user.getWorkStatus()),
                value(user.getLevel()),
                value(user.getTotalXp()),
                value(user.getCreatedAt()),
                value(lastActiveAt.get(user.getId())),
                piiIncluded ? value(user.getPhoneNumber()) : REDACTED,
                piiIncluded ? value(user.getDateOfBirth()) : REDACTED,
                value(user.getGender()),
                value(user.getPreferredLanguage()),
                value(user.getAuthProvider())));
        }
    }

    private Map<UUID, String> adminRoleNames(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> roles = new LinkedHashMap<>();
        for (AdminUser admin : adminUserRepository.findByUserIdIn(userIds)) {
            if (admin.isUsable() && admin.getUser() != null && admin.getUser().getId() != null
                && admin.getAdminRole() != null) {
                roles.putIfAbsent(admin.getUser().getId(), admin.getAdminRole().getName());
            }
        }
        return Map.copyOf(roles);
    }

    private Map<UUID, Instant> lastActiveAt(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Instant> activity = new LinkedHashMap<>();
        for (RefreshTokenRepository.LastActiveProjection row
            : refreshTokenRepository.findLastActiveByUserIdIn(userIds)) {
            if (row.getUserId() != null && row.getLastActiveAt() != null) {
                activity.putIfAbsent(row.getUserId(), row.getLastActiveAt());
            }
        }
        return Map.copyOf(activity);
    }

    private void recordAudit(
        AdminUserExportPlan plan,
        String reason,
        AuditStatus status,
        String errorCode) {
        adminAuditService.record(AdminAuditEvent.builder()
            .action(ACTION_EXPORT)
            .targetType(TARGET_TYPE_EXPORT)
            .reason(reason)
            .afterJson(adminAuditService.toJson(new ExportAuditSnapshot(
                plan.criteria(), plan.rowCount(), plan.piiIncluded())))
            .status(status)
            .errorCode(errorCode)
            .build());
    }

    private static void writeRow(Writer writer, List<String> values) throws IOException {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                writer.write(',');
            }
            writeValue(writer, values.get(index));
        }
        writer.write("\r\n");
    }

    private static void writeValue(Writer writer, String value) throws IOException {
        String safe = value == null ? "" : value;
        boolean quoted = safe.indexOf(',') >= 0
            || safe.indexOf('"') >= 0
            || safe.indexOf('\r') >= 0
            || safe.indexOf('\n') >= 0;
        if (quoted) {
            writer.write('"');
            writer.write(safe.replace("\"", "\"\""));
            writer.write('"');
        } else {
            writer.write(safe);
        }
    }

    private static String requireReason(String reason) {
        if (reason == null) {
            throw new ApiException("reason is required", "VALIDATION_ERROR");
        }
        String trimmed = reason.trim();
        if (trimmed.length() < 10 || trimmed.length() > 2_000) {
            throw new ApiException(
                "reason must contain between 10 and 2000 characters", "VALIDATION_ERROR");
        }
        return trimmed;
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record ExportAuditSnapshot(
        AdminUserListCriteria filter,
        long rowCount,
        boolean piiIncluded
    ) {
    }
}
