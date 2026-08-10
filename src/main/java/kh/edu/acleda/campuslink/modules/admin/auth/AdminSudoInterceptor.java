package kh.edu.acleda.campuslink.modules.admin.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kh.edu.acleda.campuslink.modules.admin.AdminErrorCode;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * The reusable guard behind {@link RequiresSudo} (ADM-025).
 *
 * <p>Refuses a high-tier action with {@code ADMIN_SUDO_REQUIRED} unless the caller's session
 * has an open five-minute window. Runs before the handler, so a destructive service method is
 * never entered without one.
 *
 * <h2>An interceptor rather than an aspect</h2>
 *
 * The obvious shape is {@code @Around} on the annotation, but {@code spring-boot-starter-aop}
 * is not a declared dependency of this project - {@code aspectjweaver} only appears in the
 * build graph transitively, and leaning on that for a security control is how a dependency bump
 * elsewhere quietly removes it. A {@code HandlerInterceptor} is core Spring MVC, needs nothing
 * new, and gives direct control over the error body, which the acceptance criteria require to
 * carry a specific code rather than a bare 403.
 */
@Slf4j
@Component
public class AdminSudoInterceptor implements HandlerInterceptor {

    private final AdminSudoService sudoService;
    private final ObjectMapper objectMapper;

    public AdminSudoInterceptor(AdminSudoService sudoService, ObjectMapper objectMapper) {
        this.sudoService = sudoService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
        throws Exception {

        if (!(handler instanceof HandlerMethod method) || !requiresSudo(method)) {
            return true;
        }
        if (sudoService.isOpen()) {
            return true;
        }

        // Not a warning: this is the expected first response to any high-tier action, and the
        // portal reacts by prompting for re-authentication. A warning here would be noise on
        // every legitimate delete.
        log.debug("Refused {} - no open sudo window", request.getRequestURI());

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure(
            "Confirm your identity to continue.", AdminErrorCode.ADMIN_SUDO_REQUIRED)));
        return false;
    }

    /**
     * Checks the method first, then the class.
     *
     * <p>Class-level lets a whole controller of destructive endpoints be covered in one place,
     * which is safer than annotating each method and eventually missing one.
     */
    private static boolean requiresSudo(HandlerMethod method) {
        return method.getMethodAnnotation(RequiresSudo.class) != null
            || method.getBeanType().getAnnotation(RequiresSudo.class) != null;
    }
}
