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

/** Refuses admin access tokens whose session has been signed out (ADM-024 AC#3). */
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

        // Clearing the context matters: JwtAuthenticationFilter has already populated it.
        SecurityContextHolder.clearContext();

        // Warn when a signed-out admin token is reused.
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
