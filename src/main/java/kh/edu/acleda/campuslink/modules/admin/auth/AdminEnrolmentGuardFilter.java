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

/**
 * Enforces "no admin route works until TOTP enrolment is confirmed" (SSOT §10.8, ADM-023 AC#3).
 *
 * <h2>When this can actually fire</h2>
 *
 * Not on a normal sign-in: {@code /mfa/verify} refuses an unenrolled admin, so one cannot
 * obtain an {@code ADMIN} token in the first place. The gap it closes is the one afterwards -
 * an admin whose MFA is reset while they are holding a live access token. That token stays
 * signed and unexpired for up to ten minutes, and without this filter it would keep working
 * across the entire admin surface with no second factor behind it.
 *
 * <p>It lives here, in the admin module, rather than in {@code JwtAuthenticationFilter}, so
 * that shared security code does not have to know what enrolment is (R1).
 *
 * <h2>A query per admin request, on purpose</h2>
 *
 * Enrolment state is read straight from the repository rather than cached on
 * {@code AdminIdentity}. That would be free per request, but it would add a cache-invalidation
 * obligation to every future path that changes enrolment - and a stale "enrolled" here means a
 * reset that silently did not take effect. The admin surface serves a handful of staff, so the
 * query costs nothing worth trading a coherence bug for.
 */
@Slf4j
@Component
public class AdminEnrolmentGuardFilter extends OncePerRequestFilter {

    private static final String ADMIN_PATH = "/api/v1/admin";

    /**
     * The routes that must stay reachable, or enrolment becomes impossible.
     *
     * <p>An allow-list, not a deny-list: a new admin route is protected by default rather than
     * by someone remembering to add it here.
     */
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

        // Worth a warning: a signed admin token belonging to an account with no confirmed
        // second factor is either a reset that has just taken effect, or something stranger.
        log.warn("Blocked {} for admin {} - TOTP enrolment is not confirmed", request.getRequestURI(), email);

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure(
            "Set up two-factor authentication before using the admin portal.",
            AdminErrorCode.ADMIN_MFA_NOT_ENROLLED)));
    }
}
