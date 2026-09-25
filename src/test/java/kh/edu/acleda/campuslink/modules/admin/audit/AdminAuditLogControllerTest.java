package kh.edu.acleda.campuslink.modules.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import kh.edu.acleda.campuslink.modules.admin.auth.RequiresSudo;
import kh.edu.acleda.campuslink.modules.admin.audit.dto.request.AdminAuditLogCriteria;
import kh.edu.acleda.campuslink.modules.admin.audit.dto.request.AdminAuditLogExportRequest;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

class AdminAuditLogControllerTest {

    private AdminAuditLogQueryService queryService;
    private AdminAuditLogExportService exportService;
    private AdminAuditLogController controller;

    @BeforeEach
    void setUp() {
        queryService = mock(AdminAuditLogQueryService.class);
        exportService = mock(AdminAuditLogExportService.class);
        controller = new AdminAuditLogController(queryService, exportService);
    }

    @Test
    void listAndDetailRequireAuditRead() throws Exception {
        Method list = AdminAuditLogController.class.getMethod(
            "list", String.class, String.class, String.class, java.time.LocalDate.class,
            java.time.LocalDate.class, String.class, int.class, int.class);
        Method detail = AdminAuditLogController.class.getMethod("detail", java.util.UUID.class);

        assertThat(list.getAnnotation(PreAuthorize.class).value())
            .isEqualTo("hasAuthority('PERM_audit:read')");
        assertThat(detail.getAnnotation(PreAuthorize.class).value())
            .isEqualTo("hasAuthority('PERM_audit:read')");
    }

    @Test
    void exportRequiresSudoAndHasNoMutationMapping() throws Exception {
        Method export = AdminAuditLogController.class.getMethod(
            "export", AdminAuditLogExportRequest.class);
        assertThat(AnnotationUtils.findAnnotation(export, RequiresSudo.class)).isNotNull();
        assertThat(export.getAnnotation(PreAuthorize.class).value())
            .isEqualTo("hasAuthority('PERM_audit:read')");

        for (Method method : AdminAuditLogController.class.getDeclaredMethods()) {
            assertThat(method.getAnnotation(DeleteMapping.class)).isNull();
            assertThat(method.getAnnotation(PutMapping.class)).isNull();
        }
    }

    @Test
    void exportReturnsCsvDownload() {
        AdminAuditLogExportPlan plan = new AdminAuditLogExportPlan(
            AdminAuditLogCriteria.fromRequest(null, null, null, null, null, null, 0, 25),
            kh.edu.acleda.campuslink.modules.admin.rbac.PermissionScopeResolver.AuditLogScope
                .all(java.util.UUID.randomUUID(), "SUPER_ADMIN"),
            0,
            "Incident review export");
        when(exportService.prepare(any(AdminAuditLogExportRequest.class))).thenReturn(plan);

        ResponseEntity<StreamingResponseBody> response = controller.export(
            new AdminAuditLogExportRequest(
                null, null, null, null, null, null, "Incident review export"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType())
            .isEqualTo(org.springframework.http.MediaType.parseMediaType("text/csv;charset=UTF-8"));
        assertThat(response.getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");
    }
}
