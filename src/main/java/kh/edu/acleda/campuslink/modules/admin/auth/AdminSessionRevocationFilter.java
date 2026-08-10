package kh.edu.acleda.campuslink.modules.admin.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses admin access tokens whose session has been signed out (ADM-024 AC#3).
 *
 * <p>A JWT is valid until it expires; nothing about signing out changes that on its own. This
 * is the piece that makes {@code POST /admin/auth/logout} mean "now" rather than "within ten
 * minutes" - the difference that matters on a shared or unattended machine.
 *
 * <h2>Why here and not in JwtAuthenticationFilter</h2>
 *
 * {@code JwtAuthenticationFilter} is shared infrastructure serving both APIs, and session
 * revocation is an admin concern (R1). Admin tokens are only accepted on
 * {@code /api/v1/admin/**} anyway, so checking here covers every route one can reach.
 *
 * <p>Runs on every admin route including {@code /auth/logout}: signing out twice is harmless
 * and the second call is already a no-op, whereas exempting it would leave a revoked token
 * with one endpoint it could still call.
 */
@Slf4j
@Component
public class AdminSessionRevocationFilter extends OncePerRequestFilter {

    private static final String ADMIN_PATH = "/api/v1/admin";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AdminSessionRegistry sessionRegistry;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public AdminSessionRevocationFilter(AdminSessionRegistry sessionRegistry,
                                        JwtService jwtService,
                                        ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !(path.equals(ADMIN_PATH) || path.startsWith(ADMIN_PATH + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = bearerToken(request);
        if (token == null || !jwtService.validateToken(token) || !sessionRegistry.isRevoked(jwtService.getTokenId(token))) {
            filterChain.doFilter(request, response);
            return;
        }

        // Clearing the context matters: JwtAuthenticationFilter has already populated it, and
        // leaving an authenticated principal behind on a rejected request is how a later
        // filter or an async handler ends up trusting a signed-out session.
        SecurityContextHolder.clearContext();

        // Warn rather than debug: a token being replayed after logout is either a stale tab or
        // someone using a token they should not have. Neither is routine.
        log.warn("Refused a signed-out admin session on {}", request.getRequestURI());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure(
            "Your session has ended. Please sign in again.", "UNAUTHORIZED")));
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length());
    }
}
