package kh.edu.acleda.campuslink.modules.admin.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.response.AdminMeResponse;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.response.AdminTokenResponse;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminRole;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.auth.RefreshTokenService;
import kh.edu.acleda.campuslink.modules.auth.SecurityAuditService;
import kh.edu.acleda.campuslink.modules.auth.dto.RefreshTokenRotation;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.UnauthorizedException;
import kh.edu.acleda.campuslink.shared.security.JwtProperties;
import kh.edu.acleda.campuslink.shared.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ADM-024 - refresh, logout and {@code /me}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminSessionServiceTest {

    private static final String EMAIL = "admin@genlink.kh";
    private static final String ACCESS_TOKEN = "access.jwt.token";
    private static final String REFRESH_TOKEN = "refresh.jwt.token";
    private static final String SESSION_ID = "session-1";

    @Mock private AdminUserRepository adminUserRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private SecurityAuditService securityAuditService;
    @Mock private JwtService jwtService;

    private AdminSessionRegistry sessionRegistry;
    private AdminSudoRegistry sudoRegistry;
    private AdminSessionService service;
    private AdminUser admin;
    private User user;

    @BeforeEach
    void setUp() {
        sessionRegistry = new AdminSessionRegistry();
        JwtProperties properties = new JwtProperties();
        sudoRegistry = new AdminSudoRegistry();
        service = new AdminSessionService(adminUserRepository, refreshTokenService, sessionRegistry,
            sudoRegistry, securityAuditService, jwtService, properties);

        user = new User();
        user.setEmail(EMAIL);
        user.setFullName("Makara Ren");
        user.setTokenVersion(3);

        AdminRole role = new AdminRole();
        role.setCode("MODERATOR");
        role.setName("Moderator");

        admin = new AdminUser();
        admin.setUser(user);
        admin.setAdminRole(role);
        admin.setActive(true);
        admin.setTotpEnabled(true);
        admin.setTotpConfirmedAt(Instant.parse("2026-08-01T17:00:00Z"));
        admin.setLastLoginAt(Instant.parse("2026-08-01T17:30:00Z"));

        when(adminUserRepository.findActiveByUserEmail(EMAIL)).thenReturn(Optional.of(admin));
        when(jwtService.validateToken(ACCESS_TOKEN)).thenReturn(true);
        when(jwtService.getTokenId(ACCESS_TOKEN)).thenReturn(SESSION_ID);
        when(jwtService.generateAdminAccessToken(anyString(), org.mockito.ArgumentMatchers.anyInt(), anyString()))
            .thenReturn("new.access.token");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateWith(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(EMAIL, null,
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    // AC#1 - /me returns exactly what ADM-007 resolved.
    @Test
    void meReturnsThePermissionsTheCallerActuallyHolds() {
        authenticateWith("ROLE_ADMIN", "PERM_user:read", "PERM_user:ban", "PERM_content:moderate");

        AdminMeResponse me = service.me();

        assertThat(me.permissions())
            .containsExactly("content:moderate", "user:ban", "user:read");
    }

    /** The prefix is Spring Security's, not part of the published contract. */
    @Test
    void stripsThePermPrefix() {
        authenticateWith("PERM_user:read");
        assertThat(service.me().permissions()).containsExactly("user:read");
    }

    /** {@code ROLE_*} is the mobile role model. */
    @Test
    void excludesRoleAuthorities() {
        authenticateWith("ROLE_ADMIN", "ROLE_USER", "PERM_user:read");
        assertThat(service.me().permissions()).doesNotContain("ADMIN", "ROLE_ADMIN", "USER");
    }

    @Test
    void meReturnsIdentityAndRole() {
        authenticateWith("PERM_user:read");

        AdminMeResponse me = service.me();

        assertThat(me.email()).isEqualTo(EMAIL);
        assertThat(me.fullName()).isEqualTo("Makara Ren");
        assertThat(me.roleCode()).isEqualTo("MODERATOR");
        assertThat(me.roleName()).isEqualTo("Moderator");
        assertThat(me.totpEnabled()).isTrue();
        assertThat(me.totpConfirmedAt()).isEqualTo(Instant.parse("2026-08-01T17:00:00Z"));
        assertThat(me.lastLoginAt()).isEqualTo(Instant.parse("2026-08-01T17:30:00Z"));
    }

    /** An admin whose staff access was revoked mid-session must not keep reading /me. */
    @Test
    void meRejectsAnAccountThatIsNoLongerAnAdmin() {
        authenticateWith("PERM_user:read");
        when(adminUserRepository.findActiveByUserEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.me()).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void meRejectsAnUnauthenticatedCaller() {
        assertThatThrownBy(() -> service.me()).isInstanceOf(UnauthorizedException.class);
    }

    // AC#2 - refresh rotates.
    @Test
    void refreshRotatesAndReturnsANewPairOfTokens() {
        when(refreshTokenService.rotate(eq(REFRESH_TOKEN), any(), any(), any(), anyLong()))
            .thenReturn(new RefreshTokenRotation(user, "rotated.refresh.token"));

        AdminTokenResponse response = service.refresh(REFRESH_TOKEN);

        assertThat(response.accessToken()).isEqualTo("new.access.token");
        assertThat(response.refreshToken())
            .isEqualTo("rotated.refresh.token")
            .isNotEqualTo(REFRESH_TOKEN);
        assertThat(response.expiresInSeconds()).isEqualTo(600);
    }

    /** The replacement must carry the admin lifetime, not the mobile one. */
    @Test
    void rotationUsesTheAdminRefreshLifetime() {
        when(refreshTokenService.rotate(eq(REFRESH_TOKEN), any(), any(), any(), anyLong()))
            .thenReturn(new RefreshTokenRotation(user, "rotated.refresh.token"));

        service.refresh(REFRESH_TOKEN);

        verify(refreshTokenService).rotate(eq(REFRESH_TOKEN), any(), any(), any(),
            eq(new JwtProperties().getAdminRefreshTokenExpirationMs()));
    }

    /** Each refresh gets its own session id, so logging out of one does not kill the others. */
    @Test
    void eachRefreshMintsADistinctSessionId() {
        when(refreshTokenService.rotate(eq(REFRESH_TOKEN), any(), any(), any(), anyLong()))
            .thenReturn(new RefreshTokenRotation(user, "rotated.refresh.token"));

        service.refresh(REFRESH_TOKEN);
        service.refresh(REFRESH_TOKEN);

        org.mockito.ArgumentCaptor<String> ids = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jwtService, org.mockito.Mockito.times(2))
            .generateAdminAccessToken(anyString(), org.mockito.ArgumentMatchers.anyInt(), ids.capture());
        assertThat(ids.getAllValues()).doesNotHaveDuplicates();
    }

    /** Staff access can be revoked while a session is live. */
    @Test
    void refreshRefusesWhenAdminAccessWasRevokedMidSession() {
        when(refreshTokenService.rotate(eq(REFRESH_TOKEN), any(), any(), any(), anyLong()))
            .thenReturn(new RefreshTokenRotation(user, "rotated.refresh.token"));
        when(adminUserRepository.findActiveByUserEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh(REFRESH_TOKEN))
            .isInstanceOf(UnauthorizedException.class);
        verify(jwtService, never()).generateAdminAccessToken(anyString(), org.mockito.ArgumentMatchers.anyInt(), anyString());
    }

    /** MFA reset while a session is live: no new admin token until enrolment is confirmed again. */
    @Test
    void refreshRefusesWhenEnrolmentWasReset() {
        when(refreshTokenService.rotate(eq(REFRESH_TOKEN), any(), any(), any(), anyLong()))
            .thenReturn(new RefreshTokenRotation(user, "rotated.refresh.token"));
        admin.setTotpConfirmedAt(null);

        assertThatThrownBy(() -> service.refresh(REFRESH_TOKEN))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refreshAuditsARejection() {
        when(refreshTokenService.rotate(eq(REFRESH_TOKEN), any(), any(), any(), anyLong()))
            .thenThrow(new ApiException("Refresh token reuse detected", "TOKEN_REUSE_DETECTED"));

        assertThatThrownBy(() -> service.refresh(REFRESH_TOKEN)).isInstanceOf(ApiException.class);
        verify(securityAuditService).logFailure(eq("ADMIN_SESSION_REFRESH_FAILED"), any(), anyString());
    }

    // AC#3 - logout kills the access token now, not in ten minutes.
    @Test
    void logoutRevokesTheSessionSoTheAccessTokenStopsWorking() {
        authenticateWith("PERM_user:read");
        assertThat(sessionRegistry.isRevoked(SESSION_ID)).isFalse();

        service.logout(ACCESS_TOKEN, REFRESH_TOKEN);

        assertThat(sessionRegistry.isRevoked(SESSION_ID)).isTrue();
    }

    @Test
    void logoutRevokesTheRefreshTokenToo() {
        authenticateWith("PERM_user:read");

        service.logout(ACCESS_TOKEN, REFRESH_TOKEN);

        verify(refreshTokenService).revoke(REFRESH_TOKEN);
    }

    /** Only this session. The mobile app on the same account keeps working. */
    @Test
    void logoutLeavesOtherSessionsAlone() {
        authenticateWith("PERM_user:read");
        when(jwtService.getTokenId(ACCESS_TOKEN)).thenReturn(SESSION_ID);

        service.logout(ACCESS_TOKEN, REFRESH_TOKEN);

        assertThat(sessionRegistry.isRevoked("a-different-session")).isFalse();
        // token_version untouched - bumping it is what would have signed the phone out.
        assertThat(user.getTokenVersion()).isEqualTo(3);
        verify(refreshTokenService, never()).revoke(any(User.class));
    }

    /** A logout that can fail is one an admin walks away from believing it worked. */
    @Test
    void logoutSucceedsWithNoRefreshToken() {
        authenticateWith("PERM_user:read");

        service.logout(ACCESS_TOKEN, null);

        assertThat(sessionRegistry.isRevoked(SESSION_ID)).isTrue();
        verify(refreshTokenService, never()).revoke(anyString());
    }

    @Test
    void logoutSurvivesAnUnknownRefreshToken() {
        authenticateWith("PERM_user:read");
        org.mockito.Mockito.doThrow(new ApiException("not found"))
            .when(refreshTokenService).revoke(anyString());

        service.logout(ACCESS_TOKEN, REFRESH_TOKEN);

        assertThat(sessionRegistry.isRevoked(SESSION_ID)).isTrue();
    }

    /** A sudo window belongs to the session that opened it. */
    @Test
    void logoutClosesAnOpenSudoWindow() {
        authenticateWith("PERM_user:read");
        sudoRegistry.open(SESSION_ID);
        assertThat(sudoRegistry.isOpen(SESSION_ID)).isTrue();

        service.logout(ACCESS_TOKEN, REFRESH_TOKEN);

        assertThat(sudoRegistry.isOpen(SESSION_ID)).isFalse();
    }

    @Test
    void logoutIsAudited() {
        authenticateWith("PERM_user:read");

        service.logout(ACCESS_TOKEN, REFRESH_TOKEN);

        verify(securityAuditService).log(eq("ADMIN_LOGOUT"), eq(EMAIL), eq("SUCCESS"), any(), any());
    }

    // The registry itself
    @Test
    void registryForgetsSessionsOnceTheirTokenWouldHaveExpired() {
        AdminSessionRegistry registry = new AdminSessionRegistry();
        registry.revoke("expired", Instant.now().minusSeconds(1));

        // Past its own expiry the token is refused by signature validation anyway.
        assertThat(registry.isRevoked("expired")).isFalse();
    }

    @Test
    void registryIgnoresATokenWithNoSessionId() {
        AdminSessionRegistry registry = new AdminSessionRegistry();
        registry.revoke(null, Instant.now().plusSeconds(600));

        assertThat(registry.isRevoked(null)).isFalse();
        assertThat(registry.size()).isZero();
    }

    @Test
    void registryDoesNotGrowUnboundedly() {
        AdminSessionRegistry registry = new AdminSessionRegistry();
        for (int i = 0; i < 50; i++) {
            registry.revoke("stale-" + i, Instant.now().minusSeconds(1));
        }
        registry.revoke("live", Instant.now().plusSeconds(600));

        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void registryKeepsSessionsThatAreStillLive() {
        AdminSessionRegistry registry = new AdminSessionRegistry();
        registry.revoke(UUID.randomUUID().toString(), Instant.now().plusSeconds(600));

        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void permissionsAreSortedSoTheResponseIsStable() {
        authenticateWith("PERM_z:last", "PERM_a:first", "PERM_m:middle");
        assertThat(service.me().permissions()).isEqualTo(List.of("a:first", "m:middle", "z:last"));
    }
}
