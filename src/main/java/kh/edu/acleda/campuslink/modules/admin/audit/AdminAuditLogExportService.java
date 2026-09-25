package kh.edu.acleda.campuslink.modules.admin.audit;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import kh.edu.acleda.campuslink.modules.admin.AdminErrorCode;
import kh.edu.acleda.campuslink.modules.admin.audit.dto.request.AdminAuditLogCriteria;
import kh.edu.acleda.campuslink.modules.admin.audit.dto.request.AdminAuditLogExportRequest;
import kh.edu.acleda.campuslink.modules.admin.audit.entity.AdminAuditLog;
import kh.edu.acleda.campuslink.modules.admin.audit.enums.AuditStatus;
import kh.edu.acleda.campuslink.modules.admin.rbac.PermissionScopeResolver;
import kh.edu.acleda.campuslink.modules.admin.rbac.PermissionScopeResolver.AuditLogScope;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.ForbiddenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bounded, audited CSV export for the append-only audit log (ADM-116). */
@Service
public class AdminAuditLogExportService {

    static final int MAX_EXPORT_ROWS = 50_000;
    static final int EXPORT_BATCH_SIZE = 1_000;
    static final String ACTION_EXPORT = "AUDIT_LOG_EXPORT";
    static final String TARGET_TYPE_EXPORT = "AUDIT_LOG";

    private static final List<String> HEADERS = List.of(
        "auditLogId", "createdAt", "adminUserId", "adminName", "adminEmail", "action",
        "targetType", "targetId", "beforeJson", "afterJson", "reason", "ipAddress",
        "userAgent", "requestId", "batchId", "status", "errorCode");

    private final AdminAuditLogQueryService queryService;
    private final PermissionScopeResolver scopeResolver;
    private final AdminAuditService auditService;

    public AdminAuditLogExportService(
        AdminAuditLogQueryService queryService,
        PermissionScopeResolver scopeResolver,
        AdminAuditService auditService) {
        this.queryService = queryService;
        this.scopeResolver = scopeResolver;
        this.auditService = auditService;
    }

    /** Captures authorization, filters and the row count before the HTTP stream begins. */
    @Transactional
    public AdminAuditLogExportPlan prepare(AdminAuditLogExportRequest request) {
        if (request == null) {
            throw new ApiException("Request body is required", "VALIDATION_ERROR");
        }
        String reason = requireReason(request.reason());
        AdminAuditLogCriteria criteria = request.criteria();
        AuditLogScope scope = scopeResolver.resolveAuditLogScope();
        if (!scope.isSuperAdmin()) {
            auditService.recordFailure(
                ACTION_EXPORT, TARGET_TYPE_EXPORT, null, reason, "FORBIDDEN");
            throw new ForbiddenException(
                "Only a Super Admin can export audit logs.", "FORBIDDEN");
        }

        long rowCount = queryService.count(criteria, scope);
        AdminAuditLogExportPlan plan = new AdminAuditLogExportPlan(criteria, scope, rowCount, reason);
        if (rowCount > MAX_EXPORT_ROWS) {
            audit(plan, AuditStatus.FAILURE, AdminErrorCode.EXPORT_TOO_LARGE);
            throw new ApiException(
                "The audit log contains more than " + MAX_EXPORT_ROWS
                    + " rows. Narrow the filters and retry.",
                AdminErrorCode.EXPORT_TOO_LARGE);
        }

        audit(plan, AuditStatus.SUCCESS, null);
        return plan;
    }

    /** Writes at most {@link #MAX_EXPORT_ROWS} rows in bounded database pages. */
    @Transactional(readOnly = true)
    public void writeCsv(AdminAuditLogExportPlan plan, OutputStream output) throws IOException {
        if (plan == null || output == null) {
            throw new IllegalArgumentException("Export plan and output are required");
        }

        Writer writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
        writeRow(writer, HEADERS);
        long written = 0;
        int page = 0;
        while (written < plan.rowCount()) {
            int size = (int) Math.min(EXPORT_BATCH_SIZE, plan.rowCount() - written);
            List<AdminAuditLog> logs = queryService
                .findPage(plan.criteria(), plan.scope(), page, size)
                .getContent();
            if (logs.isEmpty()) {
                break;
            }
            for (AdminAuditLog log : logs) {
                writeRow(writer, values(log));
            }
            written += logs.size();
            page++;
            writer.flush();
            if (logs.size() < size) {
                break;
            }
        }
        writer.flush();
    }

    private void audit(AdminAuditLogExportPlan plan, AuditStatus status, String errorCode) {
        auditService.record(
            AdminAuditEvent.builder()
                .action(ACTION_EXPORT)
                .targetType(TARGET_TYPE_EXPORT)
                .reason(plan.reason())
                .afterJson(auditService.toJson(new ExportAuditSnapshot(
                    plan.criteria(), plan.rowCount())))
                .status(status)
                .errorCode(errorCode)
                .build());
    }

    private static List<String> values(AdminAuditLog log) {
        AdminUser admin = log.getAdminUser();
        User user = admin == null ? null : admin.getUser();
        return List.of(
            value(log.getId()),
            value(log.getCreatedAt()),
            value(admin == null ? null : admin.getId()),
            value(user == null ? null : user.getFullName()),
            value(user == null ? null : user.getEmail()),
            value(log.getAction()),
            value(log.getTargetType()),
            value(log.getTargetId()),
            value(log.getBeforeJson()),
            value(log.getAfterJson()),
            value(log.getReason()),
            value(log.getIpAddress()),
            value(log.getUserAgent()),
            value(log.getRequestId()),
            value(log.getBatchId()),
            value(log.getStatus()),
            value(log.getErrorCode()));
    }

    private static void writeRow(Writer writer, List<String> values) throws IOException {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                writer.write(',');
            }
            String value = values.get(index) == null ? "" : values.get(index);
            boolean quoted = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
            if (quoted) {
                writer.write('"');
                writer.write(value.replace("\"", "\"\""));
                writer.write('"');
            } else {
                writer.write(value);
            }
        }
        writer.write("\r\n");
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

    private record ExportAuditSnapshot(AdminAuditLogCriteria filter, long rowCount) {
    }
}
