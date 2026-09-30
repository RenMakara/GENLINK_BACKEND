package kh.edu.acleda.campuslink.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Set when a request is refused for a specific, reportable reason. */
    public static final String AUTH_ERROR_CODE_ATTRIBUTE = "kh.genlink.authErrorCode";

    /** Error code for a token presented to an API it was not issued for. */
    public static final String TOKEN_TYPE_INVALID = "ADMIN_TOKEN_TYPE_INVALID";

    private static final String ADMIN_PATH = "/api/v1/admin";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    /** Applies JWT authentication to the request. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtService.validateToken(token) && isTokenTypeAllowed(request, token)) {
                String username = jwtService.getUsername(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (isUsable(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    /** A token is only accepted on the API it was issued for. */
    private boolean isTokenTypeAllowed(HttpServletRequest request, String token) {
        TokenType required = requiredTokenType(request);
        // Defensive: the provider never returns null, defaulting a missing claim to MOBILE.
        TokenType actual = jwtService.getTokenType(token);
        if (actual == null) {
            actual = TokenType.MOBILE;
        }
        if (actual != required) {
            // Warn when a valid token reaches the wrong API.
            log.warn("Rejected {} token on a {} route: {}", actual, required, request.getRequestURI());
            request.setAttribute(AUTH_ERROR_CODE_ATTRIBUTE, TOKEN_TYPE_INVALID);
            return false;
        }
        return true;
    }

    private static TokenType requiredTokenType(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean adminRoute = path != null && (path.equals(ADMIN_PATH) || path.startsWith(ADMIN_PATH + "/"));
        return adminRoute ? TokenType.ADMIN : TokenType.MOBILE;
    }

    /** Building the Authentication directly skips the account-status checks. */
    private boolean isUsable(String token, UserDetails userDetails) {
        if (!userDetails.isEnabled()) {
            // Worth a warning: a banned or deleted account is still holding a signed token and actively calling the API.
            log.warn("Rejected request for disabled account: {}", userDetails.getUsername());
            return false;
        }
        if (!isTokenVersionCurrent(token, userDetails)) {
            // Debug, not warn: this is the expected outcome after logout.
            log.debug("Rejected superseded token for {}", userDetails.getUsername());
            return false;
        }
        return true;
    }

    /** A signed, unexpired token is not enough: it must also match the account's current token version. */
    private boolean isTokenVersionCurrent(String token, UserDetails userDetails) {
        Integer tokenVersion = jwtService.getTokenVersion(token);
        if (tokenVersion == null || !(userDetails instanceof AuthenticatedUser authenticatedUser)) {
            return false;
        }
        return tokenVersion == authenticatedUser.getTokenVersion();
    }
}
