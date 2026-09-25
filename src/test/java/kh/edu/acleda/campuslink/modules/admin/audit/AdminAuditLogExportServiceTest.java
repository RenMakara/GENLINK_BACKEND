package kh.edu.acleda.campuslink.modules.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.audit.dto.request.AdminAuditLogCriteria;
import kh.edu.acleda.campuslink.modules.admin.audit.dto.request.AdminAuditLogExportRequest;
import kh.edu.acleda.campuslink.modules.admin.audit.entity.AdminAuditLog;
import kh.edu.acleda.campuslink.modules.admin.rbac.PermissionScopeResolver;
import kh.edu.acleda.campuslink.shared.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

class AdminAuditLogExportServiceTest {

    @Test
    void onlySuperAdminCanPrepareAnExport() {
        AdminAuditLogQueryService queryService = mock(AdminAuditLogQueryService.class);
        PermissionScopeResolver scopeResolver = mock(PermissionScopeResolver.class);
        AdminAuditService auditService = mock(AdminAuditService.class);
        when(scopeResolver.resolveAuditLogScope()).thenReturn(
            PermissionScopeResolver.AuditLogScope.all(UUID.randomUUID(), "ADMIN"));

        AdminAuditLogExportService service = new AdminAuditLogExportService(
            queryService, scopeResolver, auditService);

        assertThatThrownBy(() -> service.prepare(new AdminAuditLogExportRequest(
            null, null, null, null, null, null, "Incident review export")))
            .isInstanceOf(ForbiddenException.class)
            .hasMessage("Only a Super Admin can export audit logs.");
        verify(auditService).recordFailure(
            "AUDIT_LOG_EXPORT", "AUDIT_LOG", null, "Incident review export", "FORBIDDEN");
    }

    @Test
    void writesBoundedCsvWithEscapedJsonFields() throws Exception {
        AdminAuditLogQueryService queryService = mock(AdminAuditLogQueryService.class);
        PermissionScopeResolver scopeResolver = mock(PermissionScopeResolver.class);
        AdminAuditService auditService = mock(AdminAuditService.class);
        PermissionScopeResolver.AuditLogScope scope =
            PermissionScopeResolver.AuditLogScope.all(UUID.randomUUID(), "SUPER_ADMIN");
        when(scopeResolver.resolveAuditLogScope()).thenReturn(scope);
        when(queryService.count(any(AdminAuditLogCriteria.class), any())).thenReturn(1L);
        AdminAuditLog log = mock(AdminAuditLog.class);
        when(log.getAction()).thenReturn("USER_UPDATE");
        when(log.getBeforeJson()).thenReturn("{\"status\":\"PENDING\"}");
        when(log.getAfterJson()).thenReturn("{\"status\":\"ACTIVE\"}");
        when(queryService.findPage(any(), any(), any(Integer.class), any(Integer.class)))
            .thenReturn(new PageImpl<>(List.of(log)));

        AdminAuditLogExportService service = new AdminAuditLogExportService(
            queryService, scopeResolver, auditService);
        AdminAuditLogExportPlan plan = service.prepare(new AdminAuditLogExportRequest(
            null, null, null, null, null, null, "Incident review export"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.writeCsv(plan, output);

        String csv = output.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).startsWith("auditLogId,createdAt,adminUserId");
        assertThat(csv).contains("USER_UPDATE");
        assertThat(csv).contains("{\"\"status\"\":\"\"PENDING\"\"}");
    }
}
