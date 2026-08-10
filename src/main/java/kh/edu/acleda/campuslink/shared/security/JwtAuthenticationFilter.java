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

    /**
     * Set when a request is refused for a specific, reportable reason. Read by
     * {@link JwtAuthenticationEntryPoint} so the caller gets that error code instead of a
     * bare {@code UNAUTHORIZED}.
     */
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

    /***
     *
     * @param request
     * @param response
     * @param filterChain
     * @throws ServletException
     * @throws IOException
     */
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

    /**
     * A token is only accepted on the API it was issued for: {@link TokenType#ADMIN} on
     * {@code /api/v1/admin/**}, {@link TokenType#MOBILE} everywhere else.
     *
     * <p>Without this, any user whose {@code users.role} happened to be {@code ADMIN} could
     * drive the entire admin surface from the phone, with the token the app already holds.
     * The check lives here rather than on each controller so it cannot be forgotten on a new
     * endpoint, and so it applies before any authorization rule runs.
     *
     * <p>The rule is symmetric on purpose. Rejecting an admin token on mobile routes keeps
     * the two sessions from being interchangeable, so a leaked admin token cannot be quietly
     * reused against the ordinary API.
     */
    private boolean isTokenTypeAllowed(HttpServletRequest request, String token) {
        TokenType required = requiredTokenType(request);
        // Defensive: the provider never returns null, defaulting a missing claim to MOBILE.
        TokenType actual = jwtService.getTokenType(token);
        if (actual == null) {
            actual = TokenType.MOBILE;
        }
        if (actual != required) {
            // Warn, not debug: a valid signed token arriving at the wrong API is either a
            // misconfigured client or someone probing the admin surface. Neither is routine.
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

    /**
     * Building the Authentication directly skips the account-status checks that
     * DaoAuthenticationProvider would normally run, so they are enforced here.
     * Without the isEnabled() check a banned or soft-deleted account keeps full
     * access for as long as its JWT stays unexpired.
     */
    private boolean isUsable(String token, UserDetails userDetails) {
        if (!userDetails.isEnabled()) {
            // Worth a warning: a banned or deleted account is still holding a signed
            // token and actively calling the API. Rejections here are otherwise
            // completely silent - nothing throws, the request simply arrives
            // unauthenticated and turns into a bare 401.
            log.warn("Rejected request for disabled account: {}", userDetails.getUsername());
            return false;
        }
        if (!isTokenVersionCurrent(token, userDetails)) {
            // Debug, not warn: this is the expected outcome after logout, a password
            // change or revoke-all, and a client retrying once is normal. A sudden
            // flood of these means tokens are being replayed.
            log.debug("Rejected superseded token for {}", userDetails.getUsername());
            return false;
        }
        return true;
    }

    /**
     * A signed, unexpired token is not enough: it must also match the account's current
     * token version. This is what makes logout, password change and revoke-all take
     * effect immediately rather than when the token expires.
     *
     * <p>Fails closed - a token with no version claim (issued before versioning) or an
     * unexpected UserDetails type is rejected. The user object is already loaded above,
     * so this costs no extra query.
     */
    private boolean isTokenVersionCurrent(String token, UserDetails userDetails) {
        Integer tokenVersion = jwtService.getTokenVersion(token);
        if (tokenVersion == null || !(userDetails instanceof AuthenticatedUser authenticatedUser)) {
            return false;
        }
        return tokenVersion == authenticatedUser.getTokenVersion();
    }
}
