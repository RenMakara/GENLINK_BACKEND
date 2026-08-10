package kh.edu.acleda.campuslink.modules.admin.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.request.AdminRefreshRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.CookieValue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADM-029 - the admin refresh route must never authenticate by cookie.
 *
 * <h2>Why this is a test and not a comment</h2>
 *
 * SSOT §10.9 asked for {@code CookieCsrfTokenRepository} on this route, on the assumption that
 * it reads the refresh token from the {@code httpOnly} cookie. It does not: ADM-024 built it to
 * take the token from the request <em>body</em>, and §12 puts the cookie in the Next.js route
 * handlers. The browser's cookie-authenticated hop is browser to Next.js; the Next.js to Spring
 * hop is server-to-server.
 *
 * <p>That makes this route CSRF-immune <strong>by construction</strong> - a cross-site attacker
 * cannot read an {@code httpOnly} cookie to place it in a body, and cannot guess the token. The
 * immunity is a property of how the parameter is bound, so it survives exactly as long as
 * nobody adds a cookie fallback "for convenience".
 *
 * <p>Adding one would create a genuine CSRF hole silently: under the MVP's path-based routing
 * the portal and the API share an origin, so the browser would attach the refresh cookie to
 * this endpoint automatically, and any site the admin visited could then trigger a refresh.
 * Nothing else in the codebase would look different. Hence a test.
 *
 * <p>CSRF protection for the real cookie hop lives in the Next.js route handler
 * ({@code app/api/auth/[...route]/route.ts}) - decision recorded 2026-08-08, SSOT §10.9 amended.
 */
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

    /**
     * A raw {@code HttpServletRequest} is the back door: it exposes {@code getCookies()}, so
     * accepting one would let a cookie fallback appear without any annotation to notice.
     */
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

    /**
     * The whole controller, not just this one method. A future cookie-authenticated admin route
     * would need the CSRF question answered again rather than inheriting silence.
     */
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

    /**
     * An absent or blank token must be rejected by validation rather than treated as "look
     * somewhere else for it".
     */
    @Test
    void theRequestRecordRequiresANonBlankToken() throws Exception {
        assertThat(AdminRefreshRequest.class.getDeclaredField("refreshToken")
            .isAnnotationPresent(jakarta.validation.constraints.NotBlank.class))
            .isTrue();
    }
}
