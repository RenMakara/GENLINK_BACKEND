package kh.edu.acleda.campuslink.modules.auth;

import kh.edu.acleda.campuslink.modules.auth.dto.*;
import kh.edu.acleda.campuslink.modules.auth.entity.OtpPurpose;
import kh.edu.acleda.campuslink.modules.auth.entity.Provider;
import kh.edu.acleda.campuslink.modules.auth.entity.SocialAccount;
import kh.edu.acleda.campuslink.modules.auth.repository.SocialAccountRepository;
import kh.edu.acleda.campuslink.modules.auth.security.AuthRateLimitService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.UserService;
import kh.edu.acleda.campuslink.modules.user.dto.UserResponse;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.AuthProvider;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.modules.user.entity.WorkStatus;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.security.JwtService;
import kh.edu.acleda.campuslink.shared.security.RequestMetadataUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final RefreshTokenService refreshTokenService;
    private final OtpService otpService;
    private final SocialAccountRepository socialAccountRepository;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final AuthRateLimitService authRateLimitService;
    private final SecurityAuditService securityAuditService;
    private final SuspiciousLoginNotifier suspiciousLoginNotifier;

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Attempting login for user: {}", request.email());
        String clientIp = RequestMetadataUtil.getCurrentClientIp();
        String userAgent = RequestMetadataUtil.getCurrentUserAgent();
        String deviceInfo = RequestMetadataUtil.getCurrentDeviceInfo();
        authRateLimitService.checkLoginAllowed(request.email(), clientIp);
        
        User user = userRepository.findByEmail(request.email())
            .orElse(null);

        if (user == null) {
            authRateLimitService.recordLoginFailure(request.email(), clientIp);
            securityAuditService.logFailure("LOGIN_FAILED", request.email(), AuthErrorCode.AUTH_INVALID_CREDENTIALS);
            throw new ApiException("Invalid email or password", AuthErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        ensureCanAuthenticate(user);
        if (user.getPasswordHash() == null) {
            authRateLimitService.recordLoginFailure(request.email(), clientIp);
            securityAuditService.logFailure("LOGIN_FAILED", user, request.email(), AuthErrorCode.AUTH_INVALID_CREDENTIALS);
            throw new ApiException("Invalid email or password", AuthErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (AuthenticationException ex) {
            authRateLimitService.recordLoginFailure(request.email(), clientIp);
            securityAuditService.logFailure("LOGIN_FAILED", user, request.email(), AuthErrorCode.AUTH_INVALID_CREDENTIALS);
            throw new ApiException("Invalid email or password", AuthErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        String token = jwtService.generateAccessToken(user.getEmail(), user.getTokenVersion());
        String refreshToken = refreshTokenService.create(user, deviceInfo, clientIp, userAgent);
        authRateLimitService.clearLoginFailures(request.email(), clientIp);
        suspiciousLoginNotifier.notifyIfNeeded(user, clientIp, userAgent);
        securityAuditService.logSuccess("LOGIN_SUCCESS", user, request.email());

        log.info("User {} successfully authenticated", request.email());
        return new AuthResponse(token, refreshToken, "Bearer", userService.toResponse(user));
    }

    @Override
    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        log.info("Attempting Google social login");
        String clientIp = RequestMetadataUtil.getCurrentClientIp();
        String userAgent = RequestMetadataUtil.getCurrentUserAgent();
        String deviceInfo = RequestMetadataUtil.getCurrentDeviceInfo();
        GoogleTokenVerifier.GoogleUser tokenData = googleTokenVerifier.verify(request.idToken());
        String email = tokenData.email();
        String uid = tokenData.sub();
        String name = tokenData.name();
        String pictureUrl = tokenData.picture();

        User user = userRepository.findByEmail(email)
            .map(existingUser -> {
                log.info("Updating existing user: {}", email);
                if (existingUser.getAccountStatus() == AccountStatus.BANNED) {
                    throw new ApiException("Your account has been banned");
                }
                if (existingUser.getAccountStatus() == AccountStatus.DELETED || existingUser.getDeletedAt() != null) {
                    throw new ApiException("Your account has been deleted");
                }
                existingUser.setGoogleId(uid);
                existingUser.setFullName(name != null ? name : existingUser.getFullName());
                existingUser.setAvatarUrl(pictureUrl != null ? pictureUrl : existingUser.getAvatarUrl());
                existingUser.setAuthProvider(AuthProvider.GOOGLE);
                existingUser.setEmailVerified(true); // Social login verifies email
                existingUser.setAccountStatus(AccountStatus.ACTIVE);
                User savedUser = userRepository.save(existingUser);
                upsertSocialAccount(savedUser, Provider.GOOGLE, uid, email);
                return savedUser;
            })
            .orElseGet(() -> {
                log.info("Creating new user from Social: {}", email);
                User newUser = new User();
                newUser.setEmail(email);
                newUser.setFullName(name != null ? name : email);
                newUser.setUsername(generateUniqueUsername(email));
                newUser.setGoogleId(uid);
                newUser.setAvatarUrl(pictureUrl);
                newUser.setAuthProvider(AuthProvider.GOOGLE);
                newUser.setEmailVerified(true);
                newUser.setAccountStatus(AccountStatus.ACTIVE);
                newUser.setWorkStatus(WorkStatus.STUDENT);
                newUser.setPreferredLanguage(Language.ENGLISH);
                User savedUser = userRepository.save(newUser);
                upsertSocialAccount(savedUser, Provider.GOOGLE, uid, email);
                return savedUser;
            });

        String token = jwtService.generateAccessToken(user.getEmail(), user.getTokenVersion());
        String refreshToken = refreshTokenService.create(user, deviceInfo, clientIp, userAgent);
        suspiciousLoginNotifier.notifyIfNeeded(user, clientIp, userAgent);
        securityAuditService.logSuccess("LOGIN_SUCCESS", user, email);

        log.info("User {} successfully authenticated via Social login", email);
        return new AuthResponse(token, refreshToken, "Bearer", userService.toResponse(user));
    }

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.email());
        passwordPolicyValidator.validate(request.password());

        User existing = userRepository.findByEmail(request.email()).orElse(null);

        // A soft-deleted account still owns its email through the UNIQUE constraint, so
        // rejecting it here would strand the owner: unable to log in AND unable to sign
        // up again. Inside the grace period the deletion is simply undone. Past it,
        // AccountAnonymizationService has already released the address, so `existing` is
        // null and this proceeds as an ordinary new registration.
        if (existing != null && existing.getAccountStatus() == AccountStatus.DELETED) {
            reactivateDeletedAccount(existing, request);
            return;
        }

        if (existing != null) {
            log.warn("Registration failed: Email {} already exists", request.email());
            securityAuditService.logFailure("REGISTER_FAILED", request.email(), "DUPLICATE_EMAIL");
            throw new ApiException("Email already registered", "AUTH_EMAIL_ALREADY_REGISTERED");
        }

        requireUsernameAvailable(request, null);
        requirePhoneAvailable(request, null);

        User user = new User();
        user.setEmail(request.email());
        user.setUsername(request.username());
        user.setPhoneNumber(request.phoneNumber());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setUniversityName(request.universityName());
        user.setWorkStatus(request.workStatus() == null ? WorkStatus.STUDENT : request.workStatus());
        user.setPreferredLanguage(request.preferredLanguage() == null ? Language.ENGLISH : request.preferredLanguage());
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setAccountStatus(AccountStatus.PENDING);
        user.setEmailVerified(false);
        user.setTotalXp(0);
        user.setLevel(1);
        
        userRepository.save(user);
        otpService.issue(user, OtpPurpose.REGISTER);
        securityAuditService.logSuccess("REGISTER_SUCCESS", user, request.email());
        log.info("User registered and OTP sent to {}", request.email());
    }

    /**
     * Restores an account the user deleted, reusing the existing row.
     *
     * <p>This is a recovery, not a fresh signup: the same person is coming back inside
     * the grace window, so XP, badges and their existing posts are kept. Only the
     * details supplied on the form are overwritten. Once the grace period expires the
     * row is anonymised and its email freed, so a stranger who later registers a
     * recycled address never reaches this path — they get a brand new account.
     *
     * <p>The account returns as PENDING and unverified, which forces the OTP round trip:
     * proof of mailbox control is what separates the real owner from someone who merely
     * knows the address.
     */
    private void reactivateDeletedAccount(User user, RegisterRequest request) {
        requireUsernameAvailable(request, user.getId());
        requirePhoneAvailable(request, user.getId());

        user.setUsername(request.username());
        user.setPhoneNumber(request.phoneNumber());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setUniversityName(request.universityName());
        user.setWorkStatus(request.workStatus() == null ? WorkStatus.STUDENT : request.workStatus());
        user.setPreferredLanguage(request.preferredLanguage() == null ? Language.ENGLISH : request.preferredLanguage());
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setAccountStatus(AccountStatus.PENDING);
        user.setEmailVerified(false);
        user.setDeletedAt(null);
        // Tokens minted before the deletion must not spring back to life with the account.
        user.incrementTokenVersion();

        userRepository.save(user);
        otpService.issue(user, OtpPurpose.REGISTER);
        securityAuditService.logSuccess("REGISTER_REACTIVATED", user, request.email());
        log.info("Reactivated previously deleted account and OTP sent to {}", request.email());
    }

    /**
     * Rejects a username already held by a <em>different</em> account.
     *
     * @param selfId the account being reactivated, which is allowed to keep its own
     *               username; null when registering a new user, where any match is a clash
     */
    private void requireUsernameAvailable(RegisterRequest request, UUID selfId) {
        boolean takenByAnother = userRepository.findByUsername(request.username())
            .map(owner -> !owner.getId().equals(selfId))
            .orElse(false);
        if (takenByAnother) {
            log.warn("Registration failed: Username {} already exists", request.username());
            securityAuditService.logFailure("REGISTER_FAILED", request.email(), "DUPLICATE_USERNAME");
            throw new ApiException("Username already registered", "AUTH_USERNAME_ALREADY_REGISTERED");
        }
    }

    /** Phone counterpart of {@link #requireUsernameAvailable}; the field is optional. */
    private void requirePhoneAvailable(RegisterRequest request, UUID selfId) {
        if (request.phoneNumber() == null || request.phoneNumber().isBlank()) {
            return;
        }
        boolean takenByAnother = userRepository.findByPhoneNumber(request.phoneNumber())
            .map(owner -> !owner.getId().equals(selfId))
            .orElse(false);
        if (takenByAnother) {
            log.warn("Registration failed: Phone {} already exists", request.phoneNumber());
            securityAuditService.logFailure("REGISTER_FAILED", request.email(), "DUPLICATE_PHONE");
            throw new ApiException("Phone already registered", "AUTH_PHONE_ALREADY_REGISTERED");
        }
    }

    @Override
    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        log.info("Verifying email for: {}", request.email());
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new ApiException("User not found", AuthErrorCode.USER_NOT_FOUND));

        if (user.isEmailVerified()) {
            throw new ApiException("Email is already verified", AuthErrorCode.OTP_ALREADY_USED);
        }

        if (user.getAccountStatus() == AccountStatus.BANNED) {
            throw new ApiException("Your account has been banned", AuthErrorCode.AUTH_ACCOUNT_BANNED);
        }

        try {
            // A resend is issued with RESEND_VERIFICATION, so it must be checked
            // before the original REGISTER OTP. Otherwise the original record is
            // always selected and every resent code is rejected as invalid.
            otpService.verify(user, request.otp(), OtpPurpose.RESEND_VERIFICATION, OtpPurpose.REGISTER);
        } catch (ApiException ex) {
            securityAuditService.logFailure("VERIFY_OTP_FAILED", user, request.email(), ex.getErrorCode());
            throw ex;
        }
        user.setEmailVerified(true);
        user.setAccountStatus(AccountStatus.ACTIVE);
        userRepository.save(user);

        String token = jwtService.generateAccessToken(user.getEmail(), user.getTokenVersion());
        String refreshToken = refreshTokenService.create(
            user,
            RequestMetadataUtil.getCurrentDeviceInfo(),
            RequestMetadataUtil.getCurrentClientIp(),
            RequestMetadataUtil.getCurrentUserAgent());
        securityAuditService.logSuccess("VERIFY_OTP_SUCCESS", user, request.email());

        log.info("Email verified successfully for {}", request.email());
        return new AuthResponse(token, refreshToken, "Bearer", userService.toResponse(user));
    }

    @Override
    @Transactional
    public void resendOtp(String email) {
        log.info("Resending OTP to: {}", email);
        // Delegates to the hardened path. This endpoint used to answer USER_NOT_FOUND
        // and "already verified" on a public route, which let anyone enumerate which
        // emails are registered. Both routes now behave identically.
        resendVerificationOtp(new ResendVerificationOtpRequest(email));
    }

    @Override
    @Transactional
    public void resendVerificationOtp(ResendVerificationOtpRequest request) {
        log.info("Processing resend verification OTP request for: {}", request.email());
        User user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null || (user.isEmailVerified() && user.getAccountStatus() == AccountStatus.ACTIVE)) {
            securityAuditService.logSuccess("RESEND_OTP_REQUEST", null, request.email());
            return;
        }
        if (user.getAccountStatus() == AccountStatus.BANNED
            || user.getAccountStatus() == AccountStatus.DELETED
            || user.getDeletedAt() != null) {
            securityAuditService.logFailure("RESEND_OTP_BLOCKED", user, request.email(), AuthErrorCode.AUTH_ACCOUNT_NOT_ALLOWED);
            throw new ApiException("Account is not allowed to request verification OTP", AuthErrorCode.AUTH_ACCOUNT_NOT_ALLOWED);
        }

        try {
            otpService.issue(user, OtpPurpose.RESEND_VERIFICATION);
            securityAuditService.logSuccess("RESEND_OTP_REQUEST", user, request.email());
        } catch (ApiException ex) {
            securityAuditService.logFailure("RESEND_OTP_BLOCKED", user, request.email(), ex.getErrorCode());
            throw ex;
        }
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        log.info("Starting password reset for: {}", request.email());
        securityAuditService.logSuccess("FORGOT_PASSWORD_REQUEST", null, request.email());
        User user = userRepository.findByEmail(request.email()).orElse(null);

        if (user == null
            || user.getAccountStatus() == AccountStatus.BANNED
            || user.getAccountStatus() == AccountStatus.DELETED
            || user.getDeletedAt() != null
            || user.getPasswordHash() == null) {
            return;
        }

        try {
            otpService.issue(user, OtpPurpose.FORGOT_PASSWORD);
            securityAuditService.logSuccess("FORGOT_PASSWORD_REQUEST", user, request.email());
        } catch (ApiException ex) {
            securityAuditService.logFailure("FORGOT_PASSWORD_REQUEST", user, request.email(), ex.getErrorCode());
        }
        log.info("Password reset OTP sent to {}", request.email());
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        log.info("Resetting password for: {}", request.email());
        passwordPolicyValidator.validate(request.newPassword());
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new ApiException("User not found", AuthErrorCode.USER_NOT_FOUND));

        try {
            otpService.verify(user, request.otp(), OtpPurpose.FORGOT_PASSWORD);
        } catch (ApiException ex) {
            securityAuditService.logFailure("RESET_PASSWORD_FAILED", user, request.email(), ex.getErrorCode());
            throw ex;
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenService.revoke(user);
        securityAuditService.logSuccess("RESET_PASSWORD_SUCCESS", user, request.email());
        log.info("Password reset successfully for {}", request.email());
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        log.info("Processing token refresh request");
        
        RefreshTokenRotation rotation;
        try {
            rotation = refreshTokenService.rotate(
                request.refreshToken(),
                RequestMetadataUtil.getCurrentDeviceInfo(),
                RequestMetadataUtil.getCurrentClientIp(),
                RequestMetadataUtil.getCurrentUserAgent());
        } catch (ApiException ex) {
            String eventType = AuthErrorCode.TOKEN_REUSE_DETECTED.equals(ex.getErrorCode())
                ? "REFRESH_REUSE_DETECTED"
                : "REFRESH_FAILED";
            securityAuditService.logFailure(eventType, null, ex.getErrorCode());
            throw ex;
        }
        User user = rotation.user();
        String accessToken = jwtService.generateAccessToken(user.getEmail(), user.getTokenVersion());
        String newRefreshToken = rotation.refreshToken();
        securityAuditService.logSuccess("REFRESH_SUCCESS", user, user.getEmail());

        log.info("Successfully refreshed tokens for user: {}", user.getEmail());
        return new AuthResponse(accessToken, newRefreshToken, "Bearer", userService.toResponse(user));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        return userService.toResponse(userService.getUserByEmail(email));
    }

    @Override
    @Transactional
    public void logout() {
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();
            log.info("User {} is logging out, revoking refresh tokens", email);
            
            userRepository.findByEmail(email).ifPresent(user -> {
                refreshTokenService.revoke(user);
                // Also invalidates access tokens already issued; without this the
                // bearer token keeps working until it expires.
                user.incrementTokenVersion();
                userRepository.save(user);
                securityAuditService.logSuccess("LOGOUT", user, email);
            });
        }
    }

    private void ensureCanAuthenticate(User user) {
        if (user.getAccountStatus() == AccountStatus.BANNED) {
            throw new ApiException("Your account has been banned", AuthErrorCode.AUTH_ACCOUNT_BANNED);
        }
        if (user.getAccountStatus() == AccountStatus.DELETED || user.getDeletedAt() != null) {
            throw new ApiException("Your account has been deleted", AuthErrorCode.AUTH_ACCOUNT_DELETED);
        }
        if (user.getAccountStatus() == AccountStatus.PENDING || !user.isEmailVerified()) {
            throw new ApiException("Please verify your email address before logging in", AuthErrorCode.AUTH_EMAIL_NOT_VERIFIED);
        }
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ApiException("Your account is not active", AuthErrorCode.AUTH_ACCOUNT_NOT_ALLOWED);
        }
    }

    private void upsertSocialAccount(User user, Provider provider, String providerUserId, String providerEmail) {
        socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)
            .orElseGet(() -> {
                SocialAccount socialAccount = new SocialAccount();
                socialAccount.setUser(user);
                socialAccount.setProvider(provider);
                socialAccount.setProviderUserId(providerUserId);
                socialAccount.setProviderEmail(providerEmail);
                return socialAccountRepository.save(socialAccount);
            });
    }

    private String generateUniqueUsername(String email) {
        String base = email.substring(0, email.indexOf('@'))
            .toLowerCase()
            .replaceAll("[^a-z0-9_]", "_");
        if (base.isBlank()) {
            base = "user";
        }
        String candidate = base.length() > 60 ? base.substring(0, 60) : base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            String suffixText = "_" + suffix++;
            int maxBaseLength = 60 - suffixText.length();
            candidate = base.substring(0, Math.min(base.length(), maxBaseLength)) + suffixText;
        }
        return candidate;
    }
}
