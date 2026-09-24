package kh.edu.acleda.campuslink.modules.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserNoteRequest;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserNoteResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

class AdminUserNoteControllerTest {

    private AdminUserNoteService service;
    private AdminUserNoteController controller;

    @BeforeEach
    void setUp() {
        service = mock(AdminUserNoteService.class);
        controller = new AdminUserNoteController(service);
    }

    @Test
    void exposesStaffOnlyListAndCreateContracts() {
        RequestMapping mapping = AdminUserNoteController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping.value()).containsExactly("/api/v1/admin/users/{userId}/notes");
    }

    @Test
    void delegatesListAndCreateWithSeparatePermissions() throws Exception {
        UUID userId = UUID.randomUUID();
        AdminUserNoteRequest request = new AdminUserNoteRequest("Internal support context");
        AdminUserNoteResponse created = mock(AdminUserNoteResponse.class);
        PageResponse<AdminUserNoteResponse> listed = new PageResponse<>(
            java.util.List.of(), 0, 25, 0, 0, false);
        when(service.list(userId, 0, 25)).thenReturn(listed);
        when(service.create(userId, request)).thenReturn(created);

        ApiResponse<PageResponse<AdminUserNoteResponse>> listResponse =
            controller.list(userId, 0, 25);
        ApiResponse<AdminUserNoteResponse> createResponse = controller.create(userId, request);

        assertThat(listResponse.getData()).isEqualTo(listed);
        assertThat(createResponse.getData()).isEqualTo(created);

        Method list = AdminUserNoteController.class.getMethod(
            "list", UUID.class, int.class, int.class);
        Method create = AdminUserNoteController.class.getMethod(
            "create", UUID.class, AdminUserNoteRequest.class);
        assertThat(list.getAnnotation(PreAuthorize.class).value())
            .isEqualTo("hasAuthority('PERM_user:read')");
        assertThat(create.getAnnotation(PreAuthorize.class).value())
            .isEqualTo("hasAuthority('PERM_user:note:write')");
    }
}
