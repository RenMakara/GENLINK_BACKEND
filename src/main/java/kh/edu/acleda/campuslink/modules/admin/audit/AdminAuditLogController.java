package kh.edu.acleda.campuslink.modules.admin.audit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.auth.RequiresSudo;
import kh.edu.acleda.campuslink.modules.admin.audit.dto.request.AdminAuditLogCriteria;
import kh.edu.acleda.campuslink.modules.admin.audit.dto.request.AdminAuditLogExportRequest;
import kh.edu.acleda.campuslink.modules.admin.audit.dto.response.AdminAuditLogDetailResponse;
import kh.edu.acleda.campuslink.modules.admin.audit.dto.response.AdminAuditLogSummaryResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Read-only audit trail endpoints. */
@Tag(name = "Admin - Audit", description = "Append-only privileged action history")
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditLogController {

    private static final MediaType CSV_UTF_8 = MediaType.parseMediaType("text/csv;charset=UTF-8");

    private final AdminAuditLogQueryService queryService;
    private final AdminAuditLogExportService exportService;

    public AdminAuditLogController(
        AdminAuditLogQueryService queryService,
        AdminAuditLogExportService exportService) {
        this.queryService = queryService;
        this.exportService = exportService;
    }

    @Operation(
        summary = "List audit logs",
        description = "Returns newest-first audit rows. MODERATOR and FINANCE_ADMIN are limited "
            + "to their own actions by the server-side permission scope resolver.")
    @GetMapping
    @PreAuthorize("hasAuthority('PERM_audit:read')")
    public ApiResponse<PageResponse<AdminAuditLogSummaryResponse>> list(
        @RequestParam(required = false) String adminId,
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String targetType,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size) {
        AdminAuditLogCriteria criteria = AdminAuditLogCriteria.fromRequest(
            adminId, action, targetType, from, to, status, page, size);
        return ApiResponse.success("Audit logs retrieved", queryService.list(criteria));
    }

    @Operation(
        summary = "Get an audit log",
        description = "Returns full audit metadata and structured before/after JSON snapshots.")
    @GetMapping("/{auditLogId}")
    @PreAuthorize("hasAuthority('PERM_audit:read')")
    public ApiResponse<AdminAuditLogDetailResponse> detail(@PathVariable UUID auditLogId) {
        return ApiResponse.success("Audit log retrieved", queryService.detail(auditLogId));
    }

    @Operation(
        summary = "Export audit logs as CSV",
        description = "Streams up to 50,000 filtered audit rows. Requires a fresh sudo window and "
            + "a SUPER_ADMIN identity; snapshot values are never interpreted as credentials.")
    @PostMapping(value = "/export", produces = "text/csv")
    @RequiresSudo
    @PreAuthorize("hasAuthority('PERM_audit:read')")
    public ResponseEntity<StreamingResponseBody> export(
        @RequestBody AdminAuditLogExportRequest request) {
        AdminAuditLogExportPlan plan = exportService.prepare(request);
        StreamingResponseBody body = output -> exportService.writeCsv(plan, output);
        return ResponseEntity.ok()
            .contentType(CSV_UTF_8)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"genlink-audit-logs.csv\"")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(body);
    }
}
