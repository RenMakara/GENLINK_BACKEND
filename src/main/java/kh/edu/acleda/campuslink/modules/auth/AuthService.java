package kh.edu.acleda.campuslink.modules.auth;

import kh.edu.acleda.campuslink.modules.auth.dto.*;

public interface AuthService {
    AuthResponse login(LoginRequest request);
    AuthResponse googleLogin(GoogleLoginRequest request);
    void register(RegisterRequest request);
    AuthResponse verifyEmail(VerifyEmailRequest request);
    void resendOtp(String email);
    AuthResponse refreshToken(RefreshTokenRequest request);
    void logout();
}
