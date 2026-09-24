package kh.edu.acleda.campuslink.modules.admin.sanction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.request.AdminSanctionListCriteria;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.response.AdminSanctionResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.LinkedMultiValueMap;

class AdminUserSanctionReadControllerTest {

    private AdminSanctionQueryService queryService;
    private AdminUserSanctionReadController controller;

    @BeforeEach
    void setUp() {
        queryService = mock(AdminSanctionQueryService.class);
        controller = new AdminUserSanctionReadController(queryService);
    }

    @Test
    void delegatesPerUserTimelineAndTypeFilter() {
        UUID userId = UUID.randomUUID();
        PageResponse<AdminSanctionResponse> page = new PageResponse<>(
            List.of(), 0, 25, 0, 0, false);
        AdminSanctionListCriteria criteria = AdminSanctionListCriteria.fromRequest(
            "BAN,WARNING", null, null, null, null, false, 0, 25);
        when(queryService.listForUser(userId, criteria)).thenReturn(page);
        LinkedMultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.put("type", List.of("BAN", "WARNING"));

        ApiResponse<PageResponse<AdminSanctionResponse>> response = controller.list(
            userId, 0, 25, null, null, query);

        verify(queryService).listForUser(userId, criteria);
        assertThat(response.getData()).isSameAs(page);
    }

    @Test
    void exposesUserReadPermission() throws Exception {
        Method list = AdminUserSanctionReadController.class.getMethod(
            "list", UUID.class, int.class, int.class, java.time.LocalDate.class,
            java.time.LocalDate.class, org.springframework.util.MultiValueMap.class);

        assertThat(list.getAnnotation(PreAuthorize.class).value())
            .isEqualTo("hasAuthority('PERM_user:read')");
    }
}
