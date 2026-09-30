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

/** The reusable guard behind {@link RequiresSudo} (ADM-025). */
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

        // Not a warning: this is the expected first response to any high-tier action.
        log.debug("Refused {} - no open sudo window", request.getRequestURI());

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure(
            "Confirm your identity to continue.", AdminErrorCode.ADMIN_SUDO_REQUIRED)));
        return false;
    }

    /** Checks the method first, then the class. */
    private static boolean requiresSudo(HandlerMethod method) {
        return method.getMethodAnnotation(RequiresSudo.class) != null
            || method.getBeanType().getAnnotation(RequiresSudo.class) != null;
    }
}
