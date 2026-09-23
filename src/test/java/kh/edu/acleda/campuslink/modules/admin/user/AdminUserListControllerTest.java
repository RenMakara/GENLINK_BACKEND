package kh.edu.acleda.campuslink.modules.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserListCriteria;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserSummaryResponse;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminUserListControllerTest {

    private AdminUserListService service;
    private AdminUserListController controller;

    @BeforeEach
    void setUp() {
        service = mock(AdminUserListService.class);
        controller = new AdminUserListController(service);
    }

    @Test
    void returnsTheStandardPagedApiEnvelope() {
        AdminUserSummaryResponse row = new AdminUserSummaryResponse(
            UUID.randomUUID(), "Makara Ren", "makara", "makara@example.com", null, true,
            AccountStatus.ACTIVE, null, "ACME", null, 3, 120,
            Instant.parse("2026-09-01T00:00:00Z"), null);
        PageResponse<AdminUserSummaryResponse> page = new PageResponse<>(
            List.of(row), 0, 25, 1, 1, false);
        when(service.list(any(AdminUserListCriteria.class))).thenReturn(page);

        ApiResponse<PageResponse<AdminUserSummaryResponse>> response = controller.list(
            "makara", null, null, null, null, null, null, null,
            null, null, null, null, null, null, null,
            false, false, 0, 25, null);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Admin users retrieved");
        assertThat(response.getData()).isEqualTo(page);
    }

    @Test
    void requiresUserReadPermission() throws Exception {
        Method list = AdminUserListController.class.getMethod(
            "list", String.class, String.class, Boolean.class, String.class, String.class,
            String.class, String.class, String.class, Integer.class, Integer.class, Integer.class,
            Integer.class, java.time.LocalDate.class, java.time.LocalDate.class, String.class,
            boolean.class, boolean.class, int.class, int.class, List.class);
        PreAuthorize authorization = list.getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAuthority('PERM_user:read')");
    }
}
