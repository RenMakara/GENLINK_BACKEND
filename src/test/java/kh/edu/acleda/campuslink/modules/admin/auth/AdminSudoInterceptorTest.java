package kh.edu.acleda.campuslink.modules.admin.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import kh.edu.acleda.campuslink.modules.admin.AdminErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** ADM-025 AC#1 - a high-tier action without sudo is refused with the right code. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminSudoInterceptorTest {

    @Mock private AdminSudoService sudoService;

    private AdminSudoInterceptor interceptor;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        // findAndRegisterModules() so this mapper matches the one Spring injects in production.
        interceptor = new AdminSudoInterceptor(sudoService, new ObjectMapper().findAndRegisterModules());
        response = new MockHttpServletResponse();
    }

    /** Stands in for a real controller; only the annotations matter. */
    static class Handlers {
        @RequiresSudo
        public void deleteUser() {
        }

        public void listUsers() {
        }
    }

    @RequiresSudo
    static class AllHighTier {
        public void anything() {
        }
    }

    private HandlerMethod handler(Class<?> type, String method) throws Exception {
        return new HandlerMethod(type.getDeclaredConstructor().newInstance(), type.getMethod(method));
    }

    // AC#1
    @Test
    void refusesAHighTierActionWithoutSudo() throws Exception {
        when(sudoService.isOpen()).thenReturn(false);

        boolean proceed = interceptor.preHandle(
            new MockHttpServletRequest("DELETE", "/api/v1/admin/users/1"),
            response, handler(Handlers.class, "deleteUser"));

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains(AdminErrorCode.ADMIN_SUDO_REQUIRED);
    }

    /** A bare 403 would be indistinguishable from "you lack the permission entirely". */
    @Test
    void theRefusalCarriesTheCodeThePortalNeedsToPromptOn() throws Exception {
        when(sudoService.isOpen()).thenReturn(false);

        interceptor.preHandle(new MockHttpServletRequest(), response, handler(Handlers.class, "deleteUser"));

        assertThat(response.getContentAsString())
            .contains("\"success\":false")
            .contains(AdminErrorCode.ADMIN_SUDO_REQUIRED)
            .doesNotContain("FORBIDDEN");
        assertThat(response.getContentType()).contains("application/json");
    }

    @Test
    void allowsAHighTierActionWithAnOpenWindow() throws Exception {
        when(sudoService.isOpen()).thenReturn(true);

        boolean proceed = interceptor.preHandle(
            new MockHttpServletRequest(), response, handler(Handlers.class, "deleteUser"));

        assertThat(proceed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // Scope
    /** Ordinary endpoints must not require sudo, or the window becomes a login mode. */
    @Test
    void leavesUnannotatedEndpointsAlone() throws Exception {
        when(sudoService.isOpen()).thenReturn(false);

        boolean proceed = interceptor.preHandle(
            new MockHttpServletRequest(), response, handler(Handlers.class, "listUsers"));

        assertThat(proceed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    /** Class-level covers a whole controller of destructive endpoints in one place. */
    @Test
    void honoursTheAnnotationOnTheClass() throws Exception {
        when(sudoService.isOpen()).thenReturn(false);

        boolean proceed = interceptor.preHandle(
            new MockHttpServletRequest(), response, handler(AllHighTier.class, "anything"));

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    /** Static resources and error dispatches are not HandlerMethods. */
    @Test
    void ignoresNonControllerHandlers() throws Exception {
        boolean proceed = interceptor.preHandle(new MockHttpServletRequest(), response, new Object());

        assertThat(proceed).isTrue();
    }
}
