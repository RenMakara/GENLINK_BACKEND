package kh.edu.acleda.campuslink.modules.auth;

import com.google.firebase.auth.FirebaseToken;
import kh.edu.acleda.campuslink.modules.auth.dto.*;
import kh.edu.acleda.campuslink.modules.auth.entity.OtpType;
import kh.edu.acleda.campuslink.modules.auth.entity.RefreshToken;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.UserService;
import kh.edu.acleda.campuslink.modules.user.dto.UserResponse;
import kh.edu.acleda.campuslink.modules.user.entity.AuthProvider;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.modules.user.entity.UserStatus;
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

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                           JwtService jwtService,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           UserService userService,
                           FirebaseAuthService firebaseAuthService,
                           RefreshTokenService refreshTokenService,
                           OtpService otpService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userService = userService;
        this.firebaseAuthService = firebaseAuthService;
        this.refreshTokenService = refreshTokenService;
        this.otpService = otpService;
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
                if (existingUser.getStatus() == UserStatus.BANNED) {
                    throw new ApiException("Your account has been banned");
                }
                existingUser.setGoogleId(uid);
                existingUser.setFullName(name != null ? name : existingUser.getFullName());
                existingUser.setAvatarUrl(pictureUrl != null ? pictureUrl : existingUser.getAvatarUrl());
                existingUser.setAuthProvider(AuthProvider.GOOGLE);
                existingUser.setEmailVerified(true); // Social login verifies email
                existingUser.setStatus(UserStatus.ACTIVE);
                return userRepository.save(existingUser);
            })
            .orElseGet(() -> {
                log.info("Creating new user from Social: {}", email);
                User newUser = new User();
                newUser.setEmail(email);
                newUser.setFullName(name != null ? name : email);
                newUser.setGoogleId(uid);
                newUser.setAvatarUrl(pictureUrl);
                newUser.setAuthProvider(AuthProvider.GOOGLE);
                newUser.setEmailVerified(true);
                newUser.setStatus(UserStatus.ACTIVE);
                return userRepository.save(newUser);
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
        if (userRepository.existsByPhone(request.phone())) {
            log.warn("Registration failed: Phone {} already exists", request.phone());
            throw new ApiException("Phone already registered");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setUniversity(request.university());
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setStatus(UserStatus.PENDING);
        user.setEmailVerified(false);
        
        userRepository.save(user);
        otpService.issue(user, OtpType.REGISTER);
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

        if (user.getStatus() == UserStatus.BANNED) {
            throw new ApiException("Your account has been banned");
        }

        otpService.verify(user, request.otp(), OtpType.REGISTER);
        user.setEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);
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

        if (user.getStatus() == UserStatus.BANNED) {
            throw new ApiException("Your account has been banned");
        }
        otpService.issue(user, OtpType.REGISTER);
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

        otpService.issue(user, OtpType.RESET_PASSWORD);
        log.info("Password reset OTP sent to {}", request.email());
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        log.info("Resetting password for: {}", request.email());
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new ApiException("User not found"));

        otpService.verify(user, request.otp(), OtpType.RESET_PASSWORD);

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenService.revoke(user);
        log.info("Password reset successfully for {}", request.email());
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        log.info("Processing token refresh request");
        
        RefreshToken storedToken = refreshTokenService.rotate(request.refreshToken());
        User user = storedToken.getUser();
        String accessToken = jwtService.generateAccessToken(user.getEmail());
        String newRefreshToken = storedToken.getToken();

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
        if (user.getStatus() == UserStatus.BANNED) {
            throw new ApiException("Your account has been banned");
        }
        if (user.getStatus() == UserStatus.PENDING || !user.isEmailVerified()) {
            throw new ApiException("Please verify your email address before logging in");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException("Your account is not active");
        }
    }
}
