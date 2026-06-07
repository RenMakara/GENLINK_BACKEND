package kh.edu.acleda.campuslink.modules.auth;

import kh.edu.acleda.campuslink.modules.auth.dto.*;
import kh.edu.acleda.campuslink.modules.user.dto.UserResponse;

public interface AuthService {
    AuthResponse login(LoginRequest request);
    AuthResponse googleLogin(GoogleLoginRequest request);
    void register(RegisterRequest request);
    AuthResponse verifyEmail(VerifyEmailRequest request);
    void resendOtp(String email);
    void resendVerificationOtp(ResendVerificationOtpRequest request);
    void forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
    AuthResponse refreshToken(RefreshTokenRequest request);
    UserResponse getCurrentUser(String email);
    void logout();
}
