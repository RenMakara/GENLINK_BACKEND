package kh.edu.acleda.campuslink.modules.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminUniversityFacetControllerTest {

    private AdminUniversityFacetService service;
    private AdminUniversityFacetController controller;

    @BeforeEach
    void setUp() {
        service = mock(AdminUniversityFacetService.class);
        controller = new AdminUniversityFacetController(service);
    }

    @Test
    void returnsTheStandardApiEnvelope() {
        List<String> expected = List.of("Royal University of Phnom Penh");
        when(service.listUniversities("royal")).thenReturn(expected);

        ApiResponse<List<String>> response = controller.listUniversities("royal");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Admin university facets retrieved");
        assertThat(response.getData()).isEqualTo(expected);
    }

    @Test
    void requiresUserReadPermission() throws Exception {
        Method listUniversities = AdminUniversityFacetController.class.getMethod(
            "listUniversities", String.class);
        PreAuthorize authorization = listUniversities.getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAuthority('PERM_user:read')");
    }
}
