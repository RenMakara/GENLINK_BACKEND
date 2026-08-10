package kh.edu.acleda.campuslink.modules.admin.auth;

import java.time.Instant;
import java.util.Optional;
import kh.edu.acleda.campuslink.modules.admin.AdminErrorCode;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.response.AdminTokenResponse;
import kh.edu.acleda.campuslink.modules.admin.auth.totp.Base32;
import kh.edu.acleda.campuslink.modules.admin.auth.totp.RecoveryCodeService;
import kh.edu.acleda.campuslink.modules.admin.auth.totp.TotpGenerator;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.auth.RefreshTokenService;
import kh.edu.acleda.campuslink.modules.auth.SecurityAuditService;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.crypto.FieldEncryptor;
import kh.edu.acleda.campuslink.shared.exception.ForbiddenException;
import kh.edu.acleda.campuslink.shared.exception.RateLimitedException;
import kh.edu.acleda.campuslink.shared.exception.UnauthorizedException;
import kh.edu.acleda.campuslink.shared.security.JwtProperties;
import kh.edu.acleda.campuslink.shared.security.JwtService;
import kh.edu.acleda.campuslink.shared.security.RequestMetadataUtil;
import kh.edu.acleda.campuslink.shared.security.TokenType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The second factor: {@code /admin/auth/mfa/verify} (ADM-023).
 *
 * <p>Turns a challenge plus a code into a real admin session. This is the only place in the
 * application that mints a {@code typ=ADMIN} token, which is what makes "an admin session
 * always passed through TOTP" a property of the design rather than a convention.
 *
 * <h2>The challenge is spent either way</h2>
 *
 * Five wrong codes void it (SSOT §10.6); one right code retires it. Both go through
 * {@link AdminMfaChallengeStore}. Without the second half, a challenge lifted from a proxy log
 * would stay usable for the rest of its five minutes and mint a further session that needed
 * neither the password nor a code, because the real login had already supplied both.
 */
@Slf4j
@Service
public class AdminMfaVerificationService {

    private static final String EVENT_SUCCESS = "ADMIN_MFA_VERIFIED";
    private static final String EVENT_FAILED = "ADMIN_MFA_FAILED";

    /** Deliberately says nothing about which of the two code types was wrong, or why. */
    private static final String INVALID_MESSAGE = "That code is not valid.";

    private final AdminUserRepository adminUserRepository;
    private final AdminMfaChallengeStore challengeStore;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final FieldEncryptor fieldEncryptor;
    private final TotpGenerator totpGenerator;
    private final RecoveryCodeService recoveryCodeService;
    private final RefreshTokenService refreshTokenService;
    private final SecurityAuditService securityAuditService;

    public AdminMfaVerificationService(AdminUserRepository adminUserRepository,
                                       AdminMfaChallengeStore challengeStore,
                                       JwtService jwtService,
                                       JwtProperties jwtProperties,
                                       FieldEncryptor fieldEncryptor,
                                       TotpGenerator totpGenerator,
                                       RecoveryCodeService recoveryCodeService,
                                       RefreshTokenService refreshTokenService,
                                       SecurityAuditService securityAuditService) {
        this.adminUserRepository = adminUserRepository;
        this.challengeStore = challengeStore;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.fieldEncryptor = fieldEncryptor;
        this.totpGenerator = totpGenerator;
        this.recoveryCodeService = recoveryCodeService;
        this.refreshTokenService = refreshTokenService;
        this.securityAuditService = securityAuditService;
    }

    @Transactional
    public AdminTokenResponse verify(String challengeToken, String code) {
        Challenge challenge = parseChallenge(challengeToken);
        AdminUser admin = loadAdmin(challenge.email());

        if (!admin.isEnrolled()) {
            // Reachable when an admin's MFA was reset while they held a live challenge.
            securityAuditService.logFailure(EVENT_FAILED, challenge.email(), "NOT_ENROLLED");
            throw new ForbiddenException(
                "Set up two-factor authentication before signing in.",
                AdminErrorCode.ADMIN_MFA_NOT_ENROLLED);
        }

        if (challengeStore.isVoidedByAttempts(challenge.id())) {
            // Five wrong codes. This is rate limiting, so it answers 429 RATE_LIMITED rather
            // than "expired" - telling someone who mistyped that their session lapsed sends
            // them straight back round the loop instead of telling them to stop (DoD, §10.6).
            securityAuditService.logFailure(EVENT_FAILED, challenge.email(), "CHALLENGE_ATTEMPTS_EXCEEDED");
            throw new RateLimitedException(
                "Too many incorrect codes. Please sign in again to start over.");
        }

        if (!challengeStore.isUsable(challenge.id())) {
            // Already spent by a successful sign-in: a replay, not a rate limit.
            securityAuditService.logFailure(EVENT_FAILED, challenge.email(), "CHALLENGE_VOID");
            throw new UnauthorizedException("This sign-in attempt has expired. Please sign in again.");
        }

        Long remaining = accept(admin, code);
        if (remaining == null) {
            challengeStore.recordFailure(challenge.id());
            securityAuditService.logFailure(EVENT_FAILED, admin.getUser(), challenge.email(),
                "BAD_CODE(" + challengeStore.remainingAttempts(challenge.id()) + " left)");
            throw new UnauthorizedException(INVALID_MESSAGE, AdminErrorCode.ADMIN_MFA_INVALID);
        }

        challengeStore.spend(challenge.id());
        return issueSession(admin, remaining);
    }

    /**
     * Tries the code as TOTP, then as a recovery code.
     *
     * @return recovery codes left when a recovery code was spent, {@code -1L} when TOTP was
     *     used, or null when neither matched.
     */
    private Long accept(AdminUser admin, String code) {
        byte[] secret = Base32.decode(fieldEncryptor.decrypt(admin.getTotpSecret()));
        if (totpGenerator.verify(secret, code, Instant.now())) {
            return -1L;
        }

        // Only reached when the TOTP check failed, so a recovery code costs one wasted HMAC -
        // microseconds. Trying recovery first would instead cost up to ten BCrypt comparisons
        // on every ordinary sign-in, which is about a second.
        Optional<String> consumed = recoveryCodeService.consume(admin.getRecoveryCodes(), code);
        if (consumed.isEmpty()) {
            return null;
        }

        // Persisted before any token is issued. If this write were skipped or reordered, a
        // recovery code would remain spendable after use, which is AC#2 exactly.
        admin.setRecoveryCodes(consumed.get());
        adminUserRepository.save(admin);

        long remaining = recoveryCodeService.remaining(consumed.get());
        log.warn("Admin {} signed in with a recovery code; {} remaining", admin.getUser().getEmail(), remaining);
        return remaining;
    }

    private AdminTokenResponse issueSession(AdminUser admin, long recoveryCodesRemaining) {
        User user = admin.getUser();
        String clientIp = RequestMetadataUtil.getCurrentClientIp();

        // A session id, so logout can revoke this one token rather than every token the
        // person holds (ADM-024). Carried as the JWT's jti.
        String accessToken = jwtService.generateAdminAccessToken(
            user.getEmail(), user.getTokenVersion(), java.util.UUID.randomUUID().toString());
        String refreshToken = refreshTokenService.create(
            user,
            RequestMetadataUtil.getCurrentDeviceInfo(),
            clientIp,
            RequestMetadataUtil.getCurrentUserAgent(),
            jwtProperties.getAdminRefreshTokenExpirationMs());

        admin.setLastLoginAt(Instant.now());
        admin.setLastLoginIp(clientIp);
        adminUserRepository.save(admin);

        securityAuditService.logSuccess(EVENT_SUCCESS, user, user.getEmail());
        log.info("Admin {} signed in", user.getEmail());

        return new AdminTokenResponse(
            accessToken,
            refreshToken,
            "Bearer",
            jwtProperties.getAdminAccessTokenExpirationMs() / 1000,
            // -1 is the "used TOTP" marker from accept(); the client should see nothing.
            recoveryCodesRemaining < 0 ? null : recoveryCodesRemaining);
    }

    private record Challenge(String email, String id) {
    }

    /**
     * Validates the challenge token. Same checks as the enrolment routes: this endpoint is
     * {@code permitAll}, so nothing else stands in front of it.
     */
    private Challenge parseChallenge(String challengeToken) {
        if (challengeToken == null || challengeToken.isBlank() || !jwtService.validateToken(challengeToken)) {
            throw new UnauthorizedException("Your sign-in session has expired. Please sign in again.");
        }
        if (jwtService.getTokenType(challengeToken) != TokenType.ADMIN_MFA_CHALLENGE) {
            log.warn("Non-challenge token presented to /mfa/verify");
            throw new UnauthorizedException("Your sign-in session has expired. Please sign in again.");
        }
        String challengeId = jwtService.getTokenId(challengeToken);
        if (challengeId == null || challengeId.isBlank()) {
            // Every challenge this codebase issues carries a jti. One without it cannot be
            // attempt-limited or retired, so it is refused rather than trusted.
            log.warn("Challenge token with no jti presented to /mfa/verify");
            throw new UnauthorizedException("Your sign-in session has expired. Please sign in again.");
        }
        return new Challenge(jwtService.getUsername(challengeToken), challengeId);
    }

    private AdminUser loadAdmin(String email) {
        return adminUserRepository.findActiveByUserEmail(email)
            .orElseThrow(() -> new UnauthorizedException(
                "Your sign-in session has expired. Please sign in again."));
    }
}
