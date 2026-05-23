package kh.edu.acleda.campuslink.modules.auth;

import com.google.firebase.auth.FirebaseToken;
import kh.edu.acleda.campuslink.modules.auth.dto.*;
import kh.edu.acleda.campuslink.modules.auth.entity.RefreshToken;
import kh.edu.acleda.campuslink.modules.auth.repository.RefreshTokenRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.UserService;
import kh.edu.acleda.campuslink.modules.user.entity.AuthProvider;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.security.JwtProperties;
import kh.edu.acleda.campuslink.shared.security.JwtTokenProvider;
import kh.edu.acleda.campuslink.shared.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final FirebaseAuthService firebaseAuthService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final EmailService emailService;

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                           JwtTokenProvider tokenProvider,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           UserService userService,
                           FirebaseAuthService firebaseAuthService,
                           RefreshTokenRepository refreshTokenRepository,
                           JwtProperties jwtProperties,
                           EmailService emailService) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userService = userService;
        this.firebaseAuthService = firebaseAuthService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Attempting login for user: {}", request.email());
        
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new ApiException("Invalid email or password"));

        if (!user.isEmailVerified()) {
            throw new ApiException("Please verify your email address before logging in");
        }

        if (!user.isActive()) {
            throw new ApiException("Your account has been deactivated");
        }

        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        String token = tokenProvider.generateAccessToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(authentication);
        
        saveRefreshToken(user, refreshToken);

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
                existingUser.setGoogleId(uid);
                existingUser.setFullName(name != null ? name : existingUser.getFullName());
                existingUser.setAvatarUrl(pictureUrl != null ? pictureUrl : existingUser.getAvatarUrl());
                existingUser.setAuthProvider(AuthProvider.GOOGLE);
                existingUser.setEmailVerified(true); // Social login verifies email
                existingUser.setActive(true);
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
                newUser.setActive(true);
                return userRepository.save(newUser);
            });

        String token = tokenProvider.generateAccessToken(user.getEmail());
        String refreshToken = tokenProvider.generateRefreshToken(user.getEmail());
        
        saveRefreshToken(user, refreshToken);

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

        String otp = generateOtp();
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setActive(false); // Inactive until verified
        user.setEmailVerified(false);
        user.setOtpCode(otp);
        user.setOtpExpiry(Instant.now().plus(10, ChronoUnit.MINUTES));
        
        userRepository.save(user);
        emailService.sendOtpEmail(user.getEmail(), otp);
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

        if (user.getOtpCode() == null || !user.getOtpCode().equals(request.otp())) {
            throw new ApiException("Invalid verification code");
        }

        if (user.getOtpExpiry().isBefore(Instant.now())) {
            throw new ApiException("Verification code has expired");
        }

        user.setEmailVerified(true);
        user.setActive(true);
        user.setOtpCode(null);
        user.setOtpExpiry(null);
        userRepository.save(user);

        // Auto-login after successful verification
        String token = tokenProvider.generateAccessToken(user.getEmail());
        String refreshToken = tokenProvider.generateRefreshToken(user.getEmail());
        saveRefreshToken(user, refreshToken);

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

        String otp = generateOtp();
        user.setOtpCode(otp);
        user.setOtpExpiry(Instant.now().plus(10, ChronoUnit.MINUTES));
        userRepository.save(user);

        emailService.sendOtpEmail(user.getEmail(), otp);
        log.info("New OTP sent to {}", email);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        log.info("Processing token refresh request");
        
        if (!tokenProvider.validateToken(request.refreshToken())) {
            log.warn("Invalid refresh token signature/format provided");
            throw new ApiException("Invalid refresh token");
        }

        RefreshToken storedToken = refreshTokenRepository.findByToken(request.refreshToken())
            .orElseThrow(() -> new ApiException("Refresh token not found in database"));
        
        if (storedToken.isRevoked()) {
            log.warn("Attempted use of revoked refresh token for user: {}", storedToken.getUser().getEmail());
            throw new ApiException("Refresh token has been revoked");
        }
        
        if (storedToken.getExpiryDate().isBefore(Instant.now())) {
            log.warn("Refresh token has expired for user: {}", storedToken.getUser().getEmail());
            throw new ApiException("Refresh token has expired");
        }

        User user = storedToken.getUser();
        String accessToken = tokenProvider.generateAccessToken(user.getEmail());
        String newRefreshToken = tokenProvider.generateRefreshToken(user.getEmail());

        refreshTokenRepository.delete(storedToken);
        saveRefreshToken(user, newRefreshToken);

        log.info("Successfully refreshed tokens for user: {}", user.getEmail());
        return new AuthResponse(accessToken, newRefreshToken, "Bearer", userService.toResponse(user));
    }

    @Override
    @Transactional
    public void logout() {
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();
            log.info("User {} is logging out, revoking refresh tokens", email);
            
            userRepository.findByEmail(email).ifPresent(user -> {
                refreshTokenRepository.deleteByUser(user);
            });
        }
    }

    private String generateOtp() {
        Random random = new Random();
        return String.format("%06d", random.nextInt(1000000));
    }

    private void saveRefreshToken(User user, String token) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(token);
        refreshToken.setRevoked(false);
        refreshToken.setExpiryDate(Instant.now().plus(jwtProperties.getRefreshTokenTtlDays(), ChronoUnit.DAYS));
        refreshTokenRepository.save(refreshToken);
    }
}
