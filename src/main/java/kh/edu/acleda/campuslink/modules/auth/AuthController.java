package kh.edu.acleda.campuslink.modules.auth;

import jakarta.validation.Valid;
import kh.edu.acleda.campuslink.modules.auth.dto.AuthResponse;
import kh.edu.acleda.campuslink.modules.auth.dto.LoginRequest;
import kh.edu.acleda.campuslink.modules.auth.dto.RefreshTokenRequest;
import kh.edu.acleda.campuslink.modules.auth.dto.RegisterRequest;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("REST request to register user: {}", request.email());
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.success("Registration successful", response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("REST request to refresh token");
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", response));
    }
}
