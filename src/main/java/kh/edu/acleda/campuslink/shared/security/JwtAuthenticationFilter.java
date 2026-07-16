package kh.edu.acleda.campuslink.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

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
            if (jwtService.validateToken(token)) {
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
     * Building the Authentication directly skips the account-status checks that
     * DaoAuthenticationProvider would normally run, so they are enforced here.
     * Without the isEnabled() check a banned or soft-deleted account keeps full
     * access for as long as its JWT stays unexpired.
     */
    private boolean isUsable(String token, UserDetails userDetails) {
        if (!userDetails.isEnabled()) {
            return false;
        }
        return isTokenVersionCurrent(token, userDetails);
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
