package kh.edu.acleda.campuslink.modules.auth;

import com.google.firebase.auth.FirebaseToken;
import kh.edu.acleda.campuslink.modules.auth.dto.*;
import kh.edu.acleda.campuslink.modules.auth.entity.OtpPurpose;
import kh.edu.acleda.campuslink.modules.auth.entity.Provider;
import kh.edu.acleda.campuslink.modules.auth.entity.SocialAccount;
import kh.edu.acleda.campuslink.modules.auth.repository.SocialAccountRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.UserService;
import kh.edu.acleda.campuslink.modules.user.dto.UserResponse;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.AuthProvider;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.modules.user.entity.WorkStatus;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final FirebaseAuthService firebaseAuthService;
    private final RefreshTokenService refreshTokenService;
    private final OtpService otpService;
    private final SocialAccountRepository socialAccountRepository;

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                           JwtService jwtService,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           UserService userService,
                           FirebaseAuthService firebaseAuthService,
                           RefreshTokenService refreshTokenService,
                           OtpService otpService,
                           SocialAccountRepository socialAccountRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userService = userService;
        this.firebaseAuthService = firebaseAuthService;
        this.refreshTokenService = refreshTokenService;
        this.otpService = otpService;
        this.socialAccountRepository = socialAccountRepository;
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Attempting login for user: {}", request.email());
        
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new ApiException("Invalid email or password"));

        ensureCanAuthenticate(user);
        if (user.getPasswordHash() == null) {
            throw new ApiException("Invalid email or password");
        }
        
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        String token = jwtService.generateAccessToken(authentication);
        String refreshToken = refreshTokenService.create(user);

        log.info("User {} successfully authenticated", request.email());
        return new AuthResponse(token, refreshToken, "Bearer", userService.toResponse(user));
    }

    @Override
    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        log.info("Attempting Firebase Social login");
        FirebaseToken tokenData = firebaseAuthService.verifyToken(request.idToken());
        String email = tokenData.getEmail();
        String uid = tokenData.getUid();
        String name = tokenData.getName();
        String pictureUrl = tokenData.getPicture();

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
                User savedUser = userRepository.save(newUser);
                upsertSocialAccount(savedUser, Provider.GOOGLE, uid, email);
                return savedUser;
            });

        String token = jwtService.generateAccessToken(user.getEmail());
        String refreshToken = refreshTokenService.create(user);

        log.info("User {} successfully authenticated via Social login", email);
        return new AuthResponse(token, refreshToken, "Bearer", userService.toResponse(user));
    }

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.email());
        if (userRepository.existsByEmail(request.email())) {
            log.warn("Registration failed: Email {} already exists", request.email());
            throw new ApiException("Email already registered");
        }
        if (userRepository.existsByUsername(request.username())) {
            log.warn("Registration failed: Username {} already exists", request.username());
            throw new ApiException("Username already registered");
        }
        if (request.phoneNumber() != null && !request.phoneNumber().isBlank()
            && userRepository.existsByPhoneNumber(request.phoneNumber())) {
            log.warn("Registration failed: Phone {} already exists", request.phoneNumber());
            throw new ApiException("Phone already registered");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setUsername(request.username());
        user.setPhoneNumber(request.phoneNumber());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setUniversityName(request.universityName());
        user.setWorkStatus(request.workStatus() == null ? WorkStatus.STUDENT : request.workStatus());
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setAccountStatus(AccountStatus.PENDING);
        user.setEmailVerified(false);
        user.setTotalXp(0);
        user.setLevel(1);
        
        userRepository.save(user);
        otpService.issue(user, OtpPurpose.REGISTER);
        log.info("User registered and OTP sent to {}", request.email());
    }

    @Override
    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        log.info("Verifying email for: {}", request.email());
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new ApiException("User not found"));

        if (user.isEmailVerified()) {
            throw new ApiException("Email is already verified");
        }

        if (user.getAccountStatus() == AccountStatus.BANNED) {
            throw new ApiException("Your account has been banned");
        }

        otpService.verify(user, request.otp(), OtpPurpose.REGISTER, OtpPurpose.RESEND_VERIFICATION);
        user.setEmailVerified(true);
        user.setAccountStatus(AccountStatus.ACTIVE);
        userRepository.save(user);

        String token = jwtService.generateAccessToken(user.getEmail());
        String refreshToken = refreshTokenService.create(user);

        log.info("Email verified successfully for {}", request.email());
        return new AuthResponse(token, refreshToken, "Bearer", userService.toResponse(user));
    }

    @Override
    @Transactional
    public void resendOtp(String email) {
        log.info("Resending OTP to: {}", email);
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ApiException("User not found"));

        if (user.isEmailVerified()) {
            throw new ApiException("Email is already verified");
        }

        if (user.getAccountStatus() == AccountStatus.BANNED) {
            throw new ApiException("Your account has been banned");
        }
        otpService.issue(user, OtpPurpose.RESEND_VERIFICATION);
        log.info("New OTP sent to {}", email);
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        log.info("Starting password reset for: {}", request.email());
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new ApiException("User not found"));

        if (user.getPasswordHash() == null) {
            throw new ApiException("This account does not have a local password");
        }

        otpService.issue(user, OtpPurpose.FORGOT_PASSWORD);
        log.info("Password reset OTP sent to {}", request.email());
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        log.info("Resetting password for: {}", request.email());
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new ApiException("User not found"));

        otpService.verify(user, request.otp(), OtpPurpose.FORGOT_PASSWORD);

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenService.revoke(user);
        log.info("Password reset successfully for {}", request.email());
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        log.info("Processing token refresh request");
        
        RefreshTokenRotation rotation = refreshTokenService.rotate(request.refreshToken());
        User user = rotation.user();
        String accessToken = jwtService.generateAccessToken(user.getEmail());
        String newRefreshToken = rotation.refreshToken();

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
            });
        }
    }

    private void ensureCanAuthenticate(User user) {
        if (user.getAccountStatus() == AccountStatus.BANNED) {
            throw new ApiException("Your account has been banned");
        }
        if (user.getAccountStatus() == AccountStatus.DELETED || user.getDeletedAt() != null) {
            throw new ApiException("Your account has been deleted");
        }
        if (user.getAccountStatus() == AccountStatus.PENDING || !user.isEmailVerified()) {
            throw new ApiException("Please verify your email address before logging in");
        }
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ApiException("Your account is not active");
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
