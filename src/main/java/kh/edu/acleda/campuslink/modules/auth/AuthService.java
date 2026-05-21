package kh.edu.acleda.campuslink.modules.auth;

import kh.edu.acleda.campuslink.modules.auth.dto.AuthResponse;
import kh.edu.acleda.campuslink.modules.auth.dto.LoginRequest;
import kh.edu.acleda.campuslink.modules.auth.dto.RefreshTokenRequest;
import kh.edu.acleda.campuslink.modules.auth.dto.RegisterRequest;

public interface AuthService {
    AuthResponse login(LoginRequest request);
    AuthResponse register(RegisterRequest request);
    AuthResponse refreshToken(RefreshTokenRequest request);
}
