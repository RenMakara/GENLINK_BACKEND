package kh.edu.acleda.campuslink.modules.admin.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.request.AdminRefreshRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.CookieValue;

import static org.assertj.core.api.Assertions.assertThat;

/** ADM-029 - the admin refresh route must never authenticate by cookie. */
class AdminRefreshCookieBoundaryTest {

    private Method refreshHandler() throws NoSuchMethodException {
        return AdminAuthController.class.getMethod("refresh", AdminRefreshRequest.class);
    }

    @Test
    void theRefreshHandlerBindsNoCookie() throws Exception {
        for (Parameter parameter : refreshHandler().getParameters()) {
            assertThat(parameter.isAnnotationPresent(CookieValue.class))
                .as("refresh must not bind a cookie - it would become CSRF-attackable "
                    + "(SSOT §10.9, amended 2026-08-08)")
                .isFalse();
        }
    }

    /** A raw {@code HttpServletRequest} is the back door. */
    @Test
    void theRefreshHandlerDoesNotTakeARawRequest() throws Exception {
        assertThat(Arrays.stream(refreshHandler().getParameterTypes()))
            .as("no raw request object - it is how a cookie read gets in unannotated")
            .noneMatch(HttpServletRequest.class::isAssignableFrom);
    }

    /** The token arrives one way only: as the body. */
    @Test
    void theRefreshHandlerTakesTheTokenFromTheBody() throws Exception {
        assertThat(refreshHandler().getParameterTypes())
            .containsExactly(AdminRefreshRequest.class);
    }

    /** The whole controller, not just this one method. */
    @Test
    void noAdminAuthEndpointBindsACookie() {
        for (Method method : AdminAuthController.class.getDeclaredMethods()) {
            for (Parameter parameter : method.getParameters()) {
                assertThat(parameter.isAnnotationPresent(CookieValue.class))
                    .as("%s binds a cookie; CSRF protection must be reconsidered before it does",
                        method.getName())
                    .isFalse();
            }
        }
    }

    /** An absent or blank token must be rejected by validation rather than treated as "look somewhere else for it". */
    @Test
    void theRequestRecordRequiresANonBlankToken() throws Exception {
        assertThat(AdminRefreshRequest.class.getDeclaredField("refreshToken")
            .isAnnotationPresent(jakarta.validation.constraints.NotBlank.class))
            .isTrue();
    }
}
