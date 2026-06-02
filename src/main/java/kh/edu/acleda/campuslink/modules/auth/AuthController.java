package kh.edu.acleda.campuslink.modules.auth;

import jakarta.validation.Valid;
import kh.edu.acleda.campuslink.modules.auth.dto.*;
import kh.edu.acleda.campuslink.modules.user.dto.UserResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(@Valid @RequestBody VerifyEmailRequest request) {
        log.info("REST request to verify OTP: {}", request.email());
        AuthResponse response = authService.verifyEmail(request);
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully", response));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<String>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        log.info("REST request to resend OTP to: {}", request.email());
        authService.resendOtp(request.email());
        return ResponseEntity.ok(ApiResponse.success("New verification code has been sent to your email.", null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        log.info("REST request to start password reset for: {}", request.email());
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password reset code has been sent to your email.", null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        log.info("REST request to reset password for: {}", request.email());
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password has been reset successfully.", null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("REST request to refresh token");
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", response));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("REST request to refresh token");
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal UserDetails userDetails) {
        log.info("REST request to get current auth user: {}", userDetails.getUsername());
        UserResponse response = authService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("User profile retrieved", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        log.info("REST request to logout");
        authService.logout();
        return ResponseEntity.ok(ApiResponse.success("Logout successful", null));
    }
}
