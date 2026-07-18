package kh.edu.acleda.campuslink.modules.security.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.auth.PasswordPolicyValidator;
import kh.edu.acleda.campuslink.modules.auth.SecurityAuditService;
import kh.edu.acleda.campuslink.modules.auth.entity.RefreshToken;
import kh.edu.acleda.campuslink.modules.auth.entity.SecurityAuditLog;
import kh.edu.acleda.campuslink.modules.auth.repository.RefreshTokenRepository;
import kh.edu.acleda.campuslink.modules.auth.repository.SecurityAuditLogRepository;
import kh.edu.acleda.campuslink.modules.auth.security.AuthRateLimitService;
import kh.edu.acleda.campuslink.modules.security.dto.request.ChangePasswordRequest;
import kh.edu.acleda.campuslink.modules.security.dto.response.SecurityActivityResponse;
import kh.edu.acleda.campuslink.modules.security.dto.response.SessionResponse;
import kh.edu.acleda.campuslink.modules.security.service.AccountSecurityService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import kh.edu.acleda.campuslink.shared.security.RequestMetadataUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AccountSecurityServiceImpl implements AccountSecurityService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityAuditLogRepository securityAuditLogRepository;
    private final SecurityAuditService securityAuditService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;
    private final AuthRateLimitService authRateLimitService;

    public AccountSecurityServiceImpl(UserRepository userRepository,
                                      RefreshTokenRepository refreshTokenRepository,
                                      SecurityAuditLogRepository securityAuditLogRepository,
                                      SecurityAuditService securityAuditService,
                                      PasswordPolicyValidator passwordPolicyValidator,
                                      PasswordEncoder passwordEncoder,
                                      CurrentUserProvider currentUserProvider,
                                      AuthRateLimitService authRateLimitService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.securityAuditLogRepository = securityAuditLogRepository;
        this.securityAuditService = securityAuditService;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.passwordEncoder = passwordEncoder;
        this.currentUserProvider = currentUserProvider;
        this.authRateLimitService = authRateLimitService;
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = loadCurrentUser();
        String clientIp = RequestMetadataUtil.getCurrentClientIp();

        // Holding a valid access token is not a licence to brute-force the current
        // password, so this is throttled the same way login is.
        authRateLimitService.checkPasswordChangeAllowed(user.getEmail(), clientIp);

        // Social-only accounts have no password to compare against; they must use
        // the forgot-password flow to set one.
        if (user.getPasswordHash() == null) {
            throw new ApiException(
                "This account has no password set. Use forgot password to create one.",
                "AUTH_PASSWORD_NOT_SET");
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            authRateLimitService.recordPasswordChangeFailure(user.getEmail(), clientIp);
            securityAuditService.logFailure("PASSWORD_CHANGE", user, user.getEmail(), "Current password mismatch");
            throw new ApiException("Current password is incorrect", "AUTH_INVALID_CREDENTIALS");
        }

        passwordPolicyValidator.validate(request.newPassword());

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ApiException("New password must be different from the current password", "AUTH_PASSWORD_REUSED");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // A password change invalidates every existing sign-in, including this one.
        // Revoking refresh tokens alone is not enough - access tokens are stateless
        // and would keep working until they expire, so bump the token version too.
        user.incrementTokenVersion();
        userRepository.save(user);

        refreshTokenRepository.revokeByUser(user, Instant.now());
        authRateLimitService.clearPasswordChangeFailures(user.getEmail(), clientIp);
        securityAuditService.logSuccess("PASSWORD_CHANGE", user, user.getEmail());
        log.info("Password changed and all sessions revoked for user {}", user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionResponse> getActiveSessions() {
        User user = loadCurrentUser();
        return refreshTokenRepository
            .findByUserAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(user, Instant.now())
            .stream()
            .map(this::toSessionResponse)
            .toList();
    }

    /**
     * Revokes one device's refresh token. Note the access token already issued to that
     * device stays valid until it expires: token versioning is per-user, so bumping it
     * here would sign every other device out too. Per-session cut-off would need a jti
     * denylist. Use revokeAllSessions when immediate cut-off matters.
     */
    @Override
    @Transactional
    public void revokeSession(UUID sessionId) {
        User user = loadCurrentUser();
        RefreshToken token = refreshTokenRepository.findByIdAndUser(sessionId, user)
            .orElseThrow(() -> new NotFoundException("Session not found"));

        if (token.getRevokedAt() == null) {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
            securityAuditService.logSuccess("SESSION_REVOKE", user, user.getEmail());
        }
    }

    @Override
    @Transactional
    public void revokeAllSessions() {
        User user = loadCurrentUser();
        refreshTokenRepository.revokeByUser(user, Instant.now());
        user.incrementTokenVersion();
        userRepository.save(user);
        securityAuditService.logSuccess("SESSION_REVOKE_ALL", user, user.getEmail());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SecurityActivityResponse> getRecentActivity() {
        User user = loadCurrentUser();
        return securityAuditLogRepository.findTop50ByUserOrderByCreatedAtDesc(user)
            .stream()
            .map(this::toActivityResponse)
            .toList();
    }

    private User loadCurrentUser() {
        UUID userId = currentUserProvider.getCurrentUserId();
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private SessionResponse toSessionResponse(RefreshToken token) {
        return new SessionResponse(
            token.getId(),
            token.getDeviceInfo(),
            token.getIpAddress(),
            token.getUserAgent(),
            token.getCreatedAt(),
            token.getLastUsedAt(),
            token.getExpiresAt()
        );
    }

    private SecurityActivityResponse toActivityResponse(SecurityAuditLog entry) {
        return new SecurityActivityResponse(
            entry.getId(),
            entry.getEventType(),
            entry.getStatus(),
            entry.getSuccess(),
            entry.getIpAddress(),
            entry.getUserAgent(),
            entry.getFailureReason(),
            entry.getCreatedAt()
        );
    }
}
