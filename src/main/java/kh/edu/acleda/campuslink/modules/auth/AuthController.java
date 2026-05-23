package kh.edu.acleda.campuslink.modules.auth;

import jakarta.validation.Valid;
import kh.edu.acleda.campuslink.modules.auth.dto.*;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("REST request to login user: {}", request.email());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        log.info("REST request to Google login");
        AuthResponse response = authService.googleLogin(request);
        return ResponseEntity.ok(ApiResponse.success("Google login successful", response));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("REST request to register user: {}", request.email());
        authService.register(request);
        return ResponseEntity.ok(ApiResponse.success("Registration successful. Please check your email for the verification code.", null));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        log.info("REST request to verify email: {}", request.email());
        AuthResponse response = authService.verifyEmail(request);
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully", response));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<String>> resendOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        log.info("REST request to resend OTP to: {}", email);
        authService.resendOtp(email);
        return ResponseEntity.ok(ApiResponse.success("New verification code has been sent to your email.", null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("REST request to refresh token");
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        log.info("REST request to logout");
        authService.logout();
        return ResponseEntity.ok(ApiResponse.success("Logout successful", null));
    }
}
