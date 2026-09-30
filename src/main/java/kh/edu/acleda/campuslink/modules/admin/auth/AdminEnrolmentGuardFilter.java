package kh.edu.acleda.campuslink.modules.admin.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import kh.edu.acleda.campuslink.modules.admin.AdminErrorCode;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Enforces "no admin route works until TOTP enrolment is confirmed" (SSOT §10.8, ADM-023 AC#3). */
@Slf4j
@Component
public class AdminEnrolmentGuardFilter extends OncePerRequestFilter {

    private static final String ADMIN_PATH = "/api/v1/admin";

    /** The routes that must stay reachable, or enrolment becomes impossible. */
    private static final String[] ALWAYS_ALLOWED = {
        ADMIN_PATH + "/auth/login",
        ADMIN_PATH + "/auth/mfa/enrol",
        ADMIN_PATH + "/auth/mfa/confirm",
        ADMIN_PATH + "/auth/mfa/verify",
        ADMIN_PATH + "/auth/refresh",
        ADMIN_PATH + "/auth/logout",
    };

    private final AdminUserRepository adminUserRepository;
    private final ObjectMapper objectMapper;

    public AdminEnrolmentGuardFilter(AdminUserRepository adminUserRepository, ObjectMapper objectMapper) {
        this.adminUserRepository = adminUserRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !(path.equals(ADMIN_PATH) || path.startsWith(ADMIN_PATH + "/"))) {
            return true;
        }
        for (String allowed : ALWAYS_ALLOWED) {
            if (path.equals(allowed)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            // Not our call to make - let the entry point produce its 401.
            filterChain.doFilter(request, response);
            return;
        }

        String email = authentication.getName();
        boolean enrolled = adminUserRepository.findActiveByUserEmail(email)
            .map(admin -> admin.isEnrolled())
            .orElse(false);

        if (enrolled) {
            filterChain.doFilter(request, response);
            return;
        }

        // Worth a warning: a signed admin token belonging to an account.
        log.warn("Blocked {} for admin {} - TOTP enrolment is not confirmed", request.getRequestURI(), email);

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure(
            "Set up two-factor authentication before using the admin portal.",
            AdminErrorCode.ADMIN_MFA_NOT_ENROLLED)));
    }
}
