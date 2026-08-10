package kh.edu.acleda.campuslink.modules.admin.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.response.AdminMeResponse;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.response.AdminTokenResponse;
import kh.edu.acleda.campuslink.modules.admin.rbac.Permission;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.auth.RefreshTokenService;
import kh.edu.acleda.campuslink.modules.auth.SecurityAuditService;
import kh.edu.acleda.campuslink.modules.auth.dto.RefreshTokenRotation;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.UnauthorizedException;
import kh.edu.acleda.campuslink.shared.security.JwtProperties;
import kh.edu.acleda.campuslink.shared.security.JwtService;
import kh.edu.acleda.campuslink.shared.security.RequestMetadataUtil;
import kh.edu.acleda.campuslink.shared.security.TokenType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Session lifecycle for the admin portal: refresh, logout, and {@code /me} (ADM-024).
 */
@Slf4j
@Service
public class AdminSessionService {

    private static final String EVENT_REFRESHED = "ADMIN_SESSION_REFRESHED";
    private static final String EVENT_REFRESH_FAILED = "ADMIN_SESSION_REFRESH_FAILED";
    private static final String EVENT_LOGOUT = "ADMIN_LOGOUT";

    private final AdminUserRepository adminUserRepository;
    private final RefreshTokenService refreshTokenService;
    private final AdminSessionRegistry sessionRegistry;
    private final AdminSudoRegistry sudoRegistry;
    private final SecurityAuditService securityAuditService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AdminSessionService(AdminUserRepository adminUserRepository,
                               RefreshTokenService refreshTokenService,
                               AdminSessionRegistry sessionRegistry,
                               AdminSudoRegistry sudoRegistry,
                               SecurityAuditService securityAuditService,
                               JwtService jwtService,
                               JwtProperties jwtProperties) {
        this.adminUserRepository = adminUserRepository;
        this.refreshTokenService = refreshTokenService;
        this.sessionRegistry = sessionRegistry;
        this.sudoRegistry = sudoRegistry;
        this.securityAuditService = securityAuditService;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    /**
     * The caller's identity and resolved permissions.
     *
     * <p>Permissions come from the authenticated principal's own granted authorities - the very
     * collection {@code @PreAuthorize} evaluates - rather than a second lookup against roles.
     * Two independent resolutions would eventually disagree, and the failure is nasty: the
     * portal renders an action the backend then refuses, which looks like a bug in the tool
     * rather than a permission boundary.
     */
    @Transactional(readOnly = true)
    public AdminMeResponse me() {
        String email = currentEmail();
        AdminUser admin = adminUserRepository.findActiveByUserEmail(email)
            .orElseThrow(() -> new UnauthorizedException("Your session is no longer valid."));

        User user = admin.getUser();
        return new AdminMeResponse(
            admin.getId(),
            user.getEmail(),
            user.getFullName(),
            admin.getAdminRole().getCode(),
            admin.getAdminRole().getName(),
            currentPermissions(),
            admin.getLastLoginAt(),
            admin.isTotpEnabled());
    }

    /**
     * Exchanges a refresh token for a new access token, rotating the refresh token.
     *
     * <p>Rotation is what makes theft detectable: the old token is revoked as it is spent, so
     * if it is ever presented again {@code RefreshTokenService} treats that as reuse and
     * revokes every token the account holds. A stolen token is therefore usable at most once,
     * and using it locks out whoever was slower - including the thief.
     */
    @Transactional
    public AdminTokenResponse refresh(String refreshToken) {
        RefreshTokenRotation rotation;
        try {
            rotation = refreshTokenService.rotate(
                refreshToken,
                RequestMetadataUtil.getCurrentDeviceInfo(),
                RequestMetadataUtil.getCurrentClientIp(),
                RequestMetadataUtil.getCurrentUserAgent(),
                jwtProperties.getAdminRefreshTokenExpirationMs());
        } catch (RuntimeException ex) {
            securityAuditService.logFailure(EVENT_REFRESH_FAILED, null, "REFRESH_REJECTED");
            throw ex;
        }

        User user = rotation.user();

        // Checked after rotation, not before: staff access can be revoked while a session is
        // live, and a refresh token that outlives the admin profile must not mint admin tokens.
        AdminUser admin = adminUserRepository.findActiveByUserEmail(user.getEmail())
            .orElseThrow(() -> {
                securityAuditService.logFailure(EVENT_REFRESH_FAILED, user, user.getEmail(),
                    "NOT_AN_ACTIVE_ADMIN");
                return new UnauthorizedException("Your session is no longer valid.");
            });

        if (!admin.isEnrolled()) {
            securityAuditService.logFailure(EVENT_REFRESH_FAILED, user, user.getEmail(),
                "NOT_ENROLLED");
            throw new UnauthorizedException("Your session is no longer valid.");
        }

        String sessionId = UUID.randomUUID().toString();
        String accessToken = jwtService.generateAdminAccessToken(
            user.getEmail(), user.getTokenVersion(), sessionId);

        securityAuditService.logSuccess(EVENT_REFRESHED, user, user.getEmail());

        return new AdminTokenResponse(
            accessToken,
            rotation.refreshToken(),
            "Bearer",
            jwtProperties.getAdminAccessTokenExpirationMs() / 1000,
            null);
    }

    /**
     * Ends this session: the refresh token is revoked in the database, and the access token's
     * session id is registered so it stops working immediately rather than in up to ten
     * minutes.
     *
     * @param accessToken the bearer token being signed out, needed for its session id.
     * @param refreshToken optional. Absent when the portal has already dropped the cookie;
     *     logout must still succeed, because a logout that can fail is one an admin will walk
     *     away from believing it worked.
     */
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        String email = currentEmail();

        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                refreshTokenService.revoke(refreshToken);
            } catch (RuntimeException ex) {
                // An unknown or already-revoked token is the desired end state anyway.
                log.debug("Refresh token revocation during logout was a no-op: {}", ex.getMessage());
            }
        }

        if (accessToken != null && jwtService.validateToken(accessToken)) {
            String sessionId = jwtService.getTokenId(accessToken);
            sessionRegistry.revoke(sessionId,
                Instant.now().plusMillis(jwtProperties.getAdminAccessTokenExpirationMs()));
            // An open sudo window must not outlive the session that opened it. Without this a
            // signed-out session's id could be reissued while still armed for high-tier
            // actions - unlikely, and not a risk worth carrying for one line (ADM-025).
            sudoRegistry.close(sessionId);
        }

        securityAuditService.log(EVENT_LOGOUT, email, "SUCCESS", null, null);
        log.info("Admin {} signed out", email);
    }

    /**
     * Permission codes held by the caller, with Spring's {@code PERM_} prefix removed.
     *
     * <p>{@code ROLE_*} authorities are filtered out - they are the mobile role model, not the
     * admin permission model, and leaking {@code ROLE_ADMIN} into this list would invite the
     * frontend to gate on it.
     */
    private static List<String> currentPermissions() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return List.of();
        }
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(authority -> authority.startsWith(Permission.AUTHORITY_PREFIX))
            .map(authority -> authority.substring(Permission.AUTHORITY_PREFIX.length()))
            .sorted()
            .toList();
    }

    private static String currentEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new UnauthorizedException("Your session is no longer valid.");
        }
        return authentication.getName();
    }

    /** The type these endpoints require, restated so the intent is greppable. */
    static final TokenType REQUIRED_TOKEN_TYPE = TokenType.ADMIN;
}
