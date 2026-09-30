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

/** The password half of admin sign-in. */
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

    /** A throwaway BCrypt hash nothing will ever match. */
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
        // Used exactly as supplied, like the mobile flow.
        String email = request.email();
        String clientIp = RequestMetadataUtil.getCurrentClientIp();

        if (authRateLimitService.isAdminLoginBlocked(email, clientIp)) {
            // Audited: repeated blocks on the admin endpoint are the signal.
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

        // Deliberately after the password check.
        AdminUser admin = adminUserRepository.findActiveByUserEmail(email).orElse(null);
        if (admin == null) {
            throw reject(email, user, clientIp, "NOT_AN_ACTIVE_ADMIN");
        }

        authRateLimitService.clearAdminLoginFailures(email, clientIp);

        String challengeId = UUID.randomUUID().toString();
        String challengeToken = jwtService.generateAdminMfaChallengeToken(email, challengeId);
        securityAuditService.logSuccess(EVENT_CHALLENGED, user, email);

        // Log successful admin password steps, but never issued tokens.
        log.info("Admin password step accepted for {}; MFA challenge issued", email);

        return new AdminMfaChallengeResponse(
            challengeToken,
            jwtProperties.getAdminMfaChallengeExpirationMs() / 1000,
            !admin.isEnrolled());
    }

    /** Verifies the password through the configured {@link AuthenticationManager}. */
    private boolean passwordAccepted(String email, String rawPassword) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, rawPassword));
            return true;
        } catch (AuthenticationException | UnauthorizedException ex) {
            return false;
        }
    }

    /** Spends a password verification against a hash. */
    private void equaliseTiming(String rawPassword) {
        passwordEncoder.matches(rawPassword == null ? "" : rawPassword, timingDecoyHash);
    }

    /** Records the attempt and builds the one rejection every failure path throws. */
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
