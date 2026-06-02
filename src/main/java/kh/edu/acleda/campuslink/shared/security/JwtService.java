package kh.edu.acleda.campuslink.shared.security;

import org.springframework.security.core.Authentication;

public interface JwtService {
    String generateAccessToken(Authentication authentication);
    String generateAccessToken(String username);
    String generateRefreshToken(Authentication authentication);
    String generateRefreshToken(String username);
    String getUsername(String token);
    boolean validateToken(String token);
}
