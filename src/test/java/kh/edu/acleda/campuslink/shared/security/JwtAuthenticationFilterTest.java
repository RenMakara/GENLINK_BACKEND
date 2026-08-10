package kh.edu.acleda.campuslink.shared.security;

import jakarta.servlet.FilterChain;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The filter builds the Authentication itself, which skips the account-status and
 * token checks DaoAuthenticationProvider would normally run. These tests pin the
 * two things it therefore has to enforce on its own.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String TOKEN = "signed.jwt.token";

    @Mock
    private JwtService jwtService;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesWhenTokenIsValidAndVersionMatches() throws Exception {
        stubToken(3);
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(authenticatedUser(true, 3));

        filter.doFilterInternal(requestWithBearer(), new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(any(), any());
    }

    /**
     * The bug this guards: a banned or soft-deleted account kept full API access for
     * as long as its JWT stayed unexpired, because the filter never read isEnabled().
     */
    @Test
    void rejectsDisabledAccountEvenWhenTokenIsValid() throws Exception {
        // Deliberately does not stub getTokenVersion: isEnabled() must short-circuit
        // first, so a strict-stub failure here would mean the order regressed.
        when(jwtService.validateToken(TOKEN)).thenReturn(true);
        when(jwtService.getUsername(TOKEN)).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(authenticatedUser(false, 3));

        filter.doFilterInternal(requestWithBearer(), new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(any(), any());
    }

    /** A superseded token: logout, password change or revoke-all bumped the version. */
    @Test
    void rejectsTokenFromBeforeTheVersionWasBumped() throws Exception {
        stubToken(2);
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(authenticatedUser(true, 3));

        filter.doFilterInternal(requestWithBearer(), new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /** Tokens issued before versioning existed carry no claim, and must fail closed. */
    @Test
    void rejectsTokenWithNoVersionClaim() throws Exception {
        when(jwtService.validateToken(TOKEN)).thenReturn(true);
        when(jwtService.getUsername(TOKEN)).thenReturn("user@example.com");
        when(jwtService.getTokenVersion(TOKEN)).thenReturn(null);
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(authenticatedUser(true, 0));

        filter.doFilterInternal(requestWithBearer(), new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void leavesContextEmptyWhenTokenIsInvalid() throws Exception {
        when(jwtService.validateToken(TOKEN)).thenReturn(false);

        filter.doFilterInternal(requestWithBearer(), new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void ignoresRequestWithoutBearerHeader() throws Exception {
        filter.doFilterInternal(new MockHttpServletRequest(), new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(any(), any());
    }

    // ---------------------------------------------------------------------------------
    // Token type must match the API being called (SSOT §10.1).
    //
    // Regression test S1 of the five pinned in testing-plan.md. Without this check, any
    // user whose users.role happened to be ADMIN could drive the whole admin surface from
    // the phone, with the token the app already holds.
    // ---------------------------------------------------------------------------------

    /** The one that matters: a phone's token must not reach the admin API. */
    @Test
    void rejectsMobileTokenOnAdminRoute() throws Exception {
        when(jwtService.validateToken(TOKEN)).thenReturn(true);
        when(jwtService.getTokenType(TOKEN)).thenReturn(TokenType.MOBILE);
        MockHttpServletRequest request = requestWithBearer("/api/v1/admin/users");

        filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_CODE_ATTRIBUTE))
            .isEqualTo(JwtAuthenticationFilter.TOKEN_TYPE_INVALID);
        // The user is never even loaded - the type check short-circuits first.
        verify(userDetailsService, never()).loadUserByUsername(any());
        verify(filterChain).doFilter(any(), any());
    }

    /** Symmetric: the two sessions are not interchangeable in either direction. */
    @Test
    void rejectsAdminTokenOnMobileRoute() throws Exception {
        when(jwtService.validateToken(TOKEN)).thenReturn(true);
        when(jwtService.getTokenType(TOKEN)).thenReturn(TokenType.ADMIN);
        MockHttpServletRequest request = requestWithBearer("/api/v1/profile/me");

        filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_CODE_ATTRIBUTE))
            .isEqualTo(JwtAuthenticationFilter.TOKEN_TYPE_INVALID);
    }

    @Test
    void acceptsAdminTokenOnAdminRoute() throws Exception {
        stubToken(3);
        when(jwtService.getTokenType(TOKEN)).thenReturn(TokenType.ADMIN);
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(authenticatedUser(true, 3));

        filter.doFilterInternal(requestWithBearer("/api/v1/admin/users"), new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    // ---------------------------------------------------------------------------------
    // The MFA challenge token grants nothing (ADM-021 AC#3).
    //
    // It is handed out after the password step, before TOTP. If it authenticated anything,
    // the second factor would be advisory: a stolen password alone would reach the API.
    //
    // Nothing in the filter mentions it. Both tests pass because a route only ever requires
    // MOBILE or ADMIN, so a third type matches nothing - which is the property worth pinning,
    // since it is what makes a *future* route safe by default too.
    // ---------------------------------------------------------------------------------

    @Test
    void rejectsMfaChallengeTokenOnAdminRoute() throws Exception {
        when(jwtService.validateToken(TOKEN)).thenReturn(true);
        when(jwtService.getTokenType(TOKEN)).thenReturn(TokenType.ADMIN_MFA_CHALLENGE);
        MockHttpServletRequest request = requestWithBearer("/api/v1/admin/users");

        filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_CODE_ATTRIBUTE))
            .isEqualTo(JwtAuthenticationFilter.TOKEN_TYPE_INVALID);
        verify(userDetailsService, never()).loadUserByUsername(any());
    }

    /** Nor is it a way into the mobile API, which is the softer target of the two. */
    @Test
    void rejectsMfaChallengeTokenOnMobileRoute() throws Exception {
        when(jwtService.validateToken(TOKEN)).thenReturn(true);
        when(jwtService.getTokenType(TOKEN)).thenReturn(TokenType.ADMIN_MFA_CHALLENGE);
        MockHttpServletRequest request = requestWithBearer("/api/v1/profile/me");

        filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetailsService, never()).loadUserByUsername(any());
    }

    /**
     * Backwards compatibility: tokens issued before the {@code typ} claim existed carry no
     * type. They must keep working on mobile routes - deploying this should not sign every
     * existing user out - while still being refused by the admin API.
     */
    @Test
    void legacyTokenWithoutTypeClaimStillWorksOnMobileRoutes() throws Exception {
        stubToken(3);
        when(jwtService.getTokenType(TOKEN)).thenReturn(TokenType.MOBILE);
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(authenticatedUser(true, 3));

        filter.doFilterInternal(requestWithBearer("/api/v1/profile/me"), new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    /** {@code /api/v1/admin} with no trailing slash is still the admin API. */
    @Test
    void treatsAdminPathWithoutTrailingSlashAsAdminRoute() throws Exception {
        when(jwtService.validateToken(TOKEN)).thenReturn(true);
        when(jwtService.getTokenType(TOKEN)).thenReturn(TokenType.MOBILE);
        MockHttpServletRequest request = requestWithBearer("/api/v1/admin");

        filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /** A path that merely starts with the same letters is not the admin API. */
    @Test
    void doesNotTreatSimilarlyNamedPathAsAdminRoute() throws Exception {
        stubToken(3);
        when(jwtService.getTokenType(TOKEN)).thenReturn(TokenType.MOBILE);
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(authenticatedUser(true, 3));

        filter.doFilterInternal(requestWithBearer("/api/v1/administration-guides"),
            new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    private void stubToken(int tokenVersion) {
        when(jwtService.validateToken(TOKEN)).thenReturn(true);
        when(jwtService.getUsername(TOKEN)).thenReturn("user@example.com");
        when(jwtService.getTokenVersion(TOKEN)).thenReturn(tokenVersion);
    }

    private UserDetails authenticatedUser(boolean enabled, int tokenVersion) {
        return new AuthenticatedUser("user@example.com", "hash", enabled, tokenVersion,
            Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private MockHttpServletRequest requestWithBearer() {
        return requestWithBearer("/api/v1/profile/me");
    }

    private MockHttpServletRequest requestWithBearer(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        request.addHeader("Authorization", "Bearer " + TOKEN);
        return request;
    }
}
