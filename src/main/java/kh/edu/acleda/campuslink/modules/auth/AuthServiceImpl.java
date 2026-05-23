package kh.edu.acleda.campuslink.modules.auth;

import com.google.firebase.auth.FirebaseToken;
import kh.edu.acleda.campuslink.modules.auth.dto.AuthResponse;
import kh.edu.acleda.campuslink.modules.auth.dto.GoogleLoginRequest;
import kh.edu.acleda.campuslink.modules.auth.dto.LoginRequest;
import kh.edu.acleda.campuslink.modules.auth.dto.RefreshTokenRequest;
import kh.edu.acleda.campuslink.modules.auth.dto.RegisterRequest;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.UserService;
import kh.edu.acleda.campuslink.modules.user.entity.AuthProvider;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.security.JwtTokenProvider;
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
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final FirebaseAuthService firebaseAuthService;

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                           JwtTokenProvider tokenProvider,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           UserService userService,
                           FirebaseAuthService firebaseAuthService) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userService = userService;
        this.firebaseAuthService = firebaseAuthService;
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        log.info("Attempting login for user: {}", request.email());
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        String token = tokenProvider.generateAccessToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(authentication);
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new ApiException("User not found after authentication"));

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
                existingUser.setGoogleId(uid); // Using UID as googleId for consistency
                existingUser.setFullName(name != null ? name : existingUser.getFullName());
                existingUser.setAvatarUrl(pictureUrl != null ? pictureUrl : existingUser.getAvatarUrl());
                existingUser.setAuthProvider(AuthProvider.GOOGLE);
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
                newUser.setActive(true);
                return userRepository.save(newUser);
            });

        String token = tokenProvider.generateAccessToken(user.getEmail());
        String refreshToken = tokenProvider.generateRefreshToken(user.getEmail());

        log.info("User {} successfully authenticated via Social login", email);
        return new AuthResponse(token, refreshToken, "Bearer", userService.toResponse(user));
    }

    @Override
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        log.info("Processing token refresh request");
        if (!tokenProvider.validateToken(request.refreshToken())) {
            log.warn("Invalid refresh token provided");
            throw new ApiException("Invalid refresh token");
        }

        String username = tokenProvider.getUsername(request.refreshToken());
        User user = userRepository.findByEmail(username)
            .orElseThrow(() -> new ApiException("User not found from refresh token"));

        String accessToken = tokenProvider.generateAccessToken(username);
        String refreshToken = tokenProvider.generateRefreshToken(username);

        log.info("Successfully refreshed tokens for user: {}", username);
        return new AuthResponse(accessToken, refreshToken, "Bearer", userService.toResponse(user));
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.email());
        if (userRepository.existsByEmail(request.email())) {
            log.warn("Registration failed: Email {} already exists", request.email());
            throw new ApiException("Email already registered");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setAuthProvider(AuthProvider.LOCAL);
        
        user = userRepository.save(user);
        log.info("User entity saved with ID: {}", user.getId());

        // Authenticate the new user automatically after registration
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        String token = tokenProvider.generateAccessToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(authentication);
        
        log.info("User {} registered and authenticated successfully", request.email());
        return new AuthResponse(token, refreshToken, "Bearer", userService.toResponse(user));
    }
}
