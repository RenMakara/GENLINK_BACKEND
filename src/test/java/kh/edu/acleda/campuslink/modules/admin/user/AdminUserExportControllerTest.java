package kh.edu.acleda.campuslink.modules.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.auth.RequiresSudo;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserListCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

class AdminUserExportControllerTest {

    private static final String REASON = "Support export request";

    private AdminUserExportService service;
    private AdminUserExportController controller;

    @BeforeEach
    void setUp() {
        service = mock(AdminUserExportService.class);
        controller = new AdminUserExportController(service);
        when(service.prepare(any(AdminUserListCriteria.class), anyBoolean(), anyString()))
            .thenReturn(new AdminUserExportPlan(criteria(), 1, false));
    }

    @Test
    void usesTheExportPermissionAndSudoGuard() throws Exception {
        Method export = AdminUserExportController.class.getMethod(
            "export", String.class, Boolean.class, String.class, Integer.class, Integer.class,
            Integer.class, Integer.class, LocalDate.class, LocalDate.class, String.class,
            boolean.class, boolean.class, String.class, MultiValueMap.class,
            org.springframework.security.core.Authentication.class);

        assertThat(export.getAnnotation(RequiresSudo.class)).isNotNull();
        assertThat(export.getAnnotation(PreAuthorize.class).value())
            .isEqualTo("hasAuthority('PERM_user:export')");
    }

    @Test
    void returnsCsvDownloadAndResolvesPiiSeparately() {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
            "admin@example.com",
            "credentials",
            List.of(new SimpleGrantedAuthority("PERM_user:export"),
                new SimpleGrantedAuthority("PERM_user:export:pii")));
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.add("status", "ACTIVE");
        queryParams.add("status", "PENDING");
        queryParams.add("sort", "createdAt,desc");

        ResponseEntity<StreamingResponseBody> response = controller.export(
            "example.com", true, "ACME", 1, 10, 0, 500,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 25), "7d",
            false, false, REASON, queryParams, authentication);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType()).isEqualTo(
            MediaType.parseMediaType("text/csv;charset=UTF-8"));
        assertThat(response.getHeaders().getFirst("Content-Disposition"))
            .isEqualTo("attachment; filename=\"genlink-users.csv\"");
        verify(service).prepare(
            any(AdminUserListCriteria.class), org.mockito.ArgumentMatchers.eq(true), eq(REASON));
    }

    private AdminUserListCriteria criteria() {
        return AdminUserListCriteria.fromRequest(
            null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null,
            false, false, 0, 25, null);
    }
}
