package kh.edu.acleda.campuslink.modules.admin.auth;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.request.AdminLoginRequest;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.response.AdminMfaChallengeResponse;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.auth.SecurityAuditService;
import kh.edu.acleda.campuslink.modules.auth.security.AuthRateLimitService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.RateLimitedException;
import kh.edu.acleda.campuslink.shared.exception.UnauthorizedException;
import kh.edu.acleda.campuslink.shared.security.JwtProperties;
import kh.edu.acleda.campuslink.shared.security.JwtService;
import kh.edu.acleda.campuslink.shared.security.RequestMetadataUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The password half of admin sign-in.
 *
 * <h2>Every rejection looks the same</h2>
 *
 * Wrong password, unknown email, an ordinary user who is not staff, a revoked admin, a banned
 * account - all produce byte-identical responses. Anything else turns this endpoint into an
 * oracle for "which of our users are administrators", which is the list an attacker most wants
 * and the one we can least afford to hand out. It is the same reasoning already applied to
 * {@code resend-otp} in the mobile flow.
 *
 * <p>Uniformity has to cover <em>timing</em>, not just the body. Returning early for an unknown
 * email would answer in about a millisecond where a real comparison takes roughly a hundred, so
 * the two are trivially separable with a stopwatch and no account needed. Hence
 * {@link #equaliseTiming}.
 *
 * <p>The distinguishing detail goes to {@code security_audit_logs} instead, where it is useful
 * and not visible to the caller.
 */
@Slf4j
@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    /** Identical for every rejection - see the class note. */
    private static final String REJECTION_MESSAGE = "Invalid email or password";

    private static final String EVENT_FAILED = "ADMIN_LOGIN_FAILED";
    private static final String EVENT_CHALLENGED = "ADMIN_LOGIN_MFA_CHALLENGED";
    private static final String EVENT_BLOCKED = "ADMIN_LOGIN_BLOCKED";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final AdminUserRepository adminUserRepository;
    private final AuthRateLimitService authRateLimitService;
    private final SecurityAuditService securityAuditService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    /**
     * A throwaway BCrypt hash nothing will ever match, used to spend the same time verifying a
     * password for an email that does not exist.
     *
     * <p>Generated from the injected encoder rather than hard-coded, so it always carries that
     * encoder's cost factor. A literal would silently stop equalising anything the day the cost
     * is raised - and it would still look correct in review.
     */
    private final String timingDecoyHash;
    private final PasswordEncoder passwordEncoder;

    public AdminAuthServiceImpl(AuthenticationManager authenticationManager,
                                UserRepository userRepository,
                                AdminUserRepository adminUserRepository,
                                AuthRateLimitService authRateLimitService,
                                SecurityAuditService securityAuditService,
                                JwtService jwtService,
                                JwtProperties jwtProperties,
                                PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.adminUserRepository = adminUserRepository;
        this.authRateLimitService = authRateLimitService;
        this.securityAuditService = securityAuditService;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;

        byte[] noise = new byte[32];
        new SecureRandom().nextBytes(noise);
        this.timingDecoyHash = passwordEncoder.encode(Base64.getEncoder().encodeToString(noise));
    }

    @Override
    @Transactional
    public AdminMfaChallengeResponse login(AdminLoginRequest request) {
        // Used exactly as supplied, like the mobile flow. Registration stores the address as
        // typed and `findByEmail` is an exact match, so lower-casing here would make an admin
        // registered as `Name@genlink.kh` permanently unable to sign in. The rate limiter
        // normalises its own key, so buckets are still case-insensitive.
        String email = request.email();
        String clientIp = RequestMetadataUtil.getCurrentClientIp();

        if (authRateLimitService.isAdminLoginBlocked(email, clientIp)) {
            // Audited: repeated blocks on the admin endpoint are the signal that someone is
            // working through a credential list, and they are invisible unless recorded.
            securityAuditService.logFailure(EVENT_BLOCKED, email, "RATE_LIMITED");
            throw new RateLimitedException("Too many login attempts. Please try again later.");
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            equaliseTiming(request.password());
            throw reject(email, null, clientIp, "NO_SUCH_USER");
        }

        if (!passwordAccepted(email, request.password())) {
            throw reject(email, user, clientIp, "BAD_PASSWORD");
        }

        // Deliberately after the password check. Asking first would answer "is this address an
        // admin?" without any password at all, and would answer it faster than a real attempt.
        AdminUser admin = adminUserRepository.findActiveByUserEmail(email).orElse(null);
        if (admin == null) {
            throw reject(email, user, clientIp, "NOT_AN_ACTIVE_ADMIN");
        }

        authRateLimitService.clearAdminLoginFailures(email, clientIp);

        String challengeId = UUID.randomUUID().toString();
        String challengeToken = jwtService.generateAdminMfaChallengeToken(email, challengeId);
        securityAuditService.logSuccess(EVENT_CHALLENGED, user, email);

        // Info, not debug: a successful password step for an administrator is worth having in
        // the application log even when the audit table is not to hand. No token is logged.
        log.info("Admin password step accepted for {}; MFA challenge issued", email);

        return new AdminMfaChallengeResponse(
            challengeToken,
            jwtProperties.getAdminMfaChallengeExpirationMs() / 1000,
            !admin.isEnrolled());
    }

    /**
     * Verifies the password through the configured {@link AuthenticationManager}, so admin
     * sign-in uses exactly the same provider, encoder and account-status rules as every other
     * login rather than a second implementation that can drift from it.
     *
     * <p>{@link UnauthorizedException} is caught alongside {@link AuthenticationException}
     * because {@code CustomUserDetailsService} throws the former, not
     * {@code UsernameNotFoundException}, when an account disappears between the lookup above
     * and this call. It is not an {@code AuthenticationException}, so it would otherwise
     * escape as a differently-shaped 401 - and a distinguishable response is the one thing
     * this method must not produce.
     */
    private boolean passwordAccepted(String email, String rawPassword) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, rawPassword));
            return true;
        } catch (AuthenticationException | UnauthorizedException ex) {
            return false;
        }
    }

    /**
     * Spends a password verification against a hash that cannot match, so that an email with no
     * account costs the same as one with a wrong password.
     */
    private void equaliseTiming(String rawPassword) {
        passwordEncoder.matches(rawPassword == null ? "" : rawPassword, timingDecoyHash);
    }

    /**
     * Records the attempt and builds the one rejection every failure path throws.
     *
     * @param reason written to the audit row only. It must never reach the response.
     */
    private UnauthorizedException reject(String email, User user, String clientIp, String reason) {
        authRateLimitService.recordAdminLoginFailure(email, clientIp);
        if (user == null) {
            securityAuditService.logFailure(EVENT_FAILED, email, reason);
        } else {
            securityAuditService.logFailure(EVENT_FAILED, user, email, reason);
        }
        return new UnauthorizedException(REJECTION_MESSAGE);
    }
}
