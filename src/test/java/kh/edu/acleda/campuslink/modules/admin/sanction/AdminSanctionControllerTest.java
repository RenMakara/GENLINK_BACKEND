package kh.edu.acleda.campuslink.modules.admin.sanction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.request.AdminSanctionListCriteria;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.request.AdminUserSanctionRequest;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.response.AdminSanctionResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminSanctionControllerTest {

    private AdminSanctionQueryService queryService;
    private AdminSanctionService sanctionService;
    private AdminSanctionController controller;

    @BeforeEach
    void setUp() {
        queryService = mock(AdminSanctionQueryService.class);
        sanctionService = mock(AdminSanctionService.class);
        controller = new AdminSanctionController(queryService, sanctionService);
    }

    @Test
    void delegatesGlobalListAndLift() {
        PageResponse<AdminSanctionResponse> page = new PageResponse<>(
            java.util.List.of(), 0, 25, 0, 0, false);
        AdminSanctionListCriteria criteria = AdminSanctionListCriteria.fromRequest(
            "WARNING", "HIGH", null, null, null, true, 0, 25);
        when(queryService.listGlobal(criteria)).thenReturn(page);

        ApiResponse<PageResponse<AdminSanctionResponse>> response = controller.list(
            0, 25, null, null, true,
            new org.springframework.util.LinkedMultiValueMap<>(java.util.Map.of(
                "type", java.util.List.of("WARNING"),
                "severity", java.util.List.of("HIGH"))));
        UUID sanctionId = UUID.randomUUID();
        AdminUserSanctionRequest request = new AdminUserSanctionRequest("Manual review completed");
        ApiResponse<Void> liftResponse = controller.lift(sanctionId, request);

        verify(queryService).listGlobal(criteria);
        verify(sanctionService).lift(sanctionId, request);
        assertThat(response.getData()).isSameAs(page);
        assertThat(liftResponse.getMessage()).isEqualTo("Sanction lifted");
    }

    @Test
    void exposesGlobalReadAndLiftPermissions() throws Exception {
        Method list = AdminSanctionController.class.getMethod(
            "list", int.class, int.class, java.time.LocalDate.class, java.time.LocalDate.class,
            boolean.class, org.springframework.util.MultiValueMap.class);
        Method lift = AdminSanctionController.class.getMethod(
            "lift", UUID.class, AdminUserSanctionRequest.class);

        assertThat(list.getAnnotation(PreAuthorize.class).value())
            .isEqualTo("hasAuthority('PERM_user:warn')");
        assertThat(lift.getAnnotation(PreAuthorize.class).value())
            .isEqualTo("hasAuthority('PERM_user:unban')");
    }
}
