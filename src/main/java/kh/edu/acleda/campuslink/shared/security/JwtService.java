package kh.edu.acleda.campuslink.shared.security;

import org.springframework.security.core.Authentication;

public interface JwtService {
    /**
     * @param tokenVersion the user's current token version, embedded so the token can
     *                     be invalidated on logout, password change or revoke-all.
     */
    String generateAccessToken(String username, int tokenVersion);
    String generateRefreshToken(Authentication authentication);
    String generateRefreshToken(String username);
    String getUsername(String token);
    /** @return the token's version claim, or null for tokens issued before versioning. */
    Integer getTokenVersion(String token);
    boolean validateToken(String token);
}
