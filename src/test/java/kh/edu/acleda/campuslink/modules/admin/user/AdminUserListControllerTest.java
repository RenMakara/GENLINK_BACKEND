package kh.edu.acleda.campuslink.modules.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

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
            "makara", null, null, null, null, null, null, null, null, null,
            false, false, 0, 25, new LinkedMultiValueMap<>());

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Admin users retrieved");
        assertThat(response.getData()).isEqualTo(page);
    }

    @Test
    void preservesCommaAndRepeatedQueryValuesForCriteriaParsing() {
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.add("status", "ACTIVE");
        queryParams.add("status", "PENDING");
        queryParams.add("sort", "createdAt,desc");

        when(service.list(argThat(criteria ->
            criteria.statuses().size() == 2
                && criteria.sortOrders().get(0).field() == AdminUserListCriteria.SortField.CREATED_AT
                && criteria.sortOrders().get(0).direction() == AdminUserListCriteria.Direction.DESC
        ))).thenReturn(new PageResponse<>(List.of(), 0, 25, 0, 0, false));

        controller.list(
            null, null, null, null, null, null, null, null, null, null,
            false, false, 0, 25, queryParams);
    }

    @Test
    void requiresUserReadPermission() throws Exception {
        Method list = AdminUserListController.class.getMethod(
            "list", String.class, Boolean.class, String.class, Integer.class, Integer.class,
            Integer.class, Integer.class, java.time.LocalDate.class, java.time.LocalDate.class,
            String.class, boolean.class, boolean.class, int.class, int.class, MultiValueMap.class);
        PreAuthorize authorization = list.getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAuthority('PERM_user:read')");
    }
}
