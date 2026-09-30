package kh.edu.acleda.campuslink.shared.security;

import org.springframework.security.core.Authentication;

public interface JwtService {
    /** Issues a {@link TokenType#MOBILE} access token. */
    String generateAccessToken(String username, int tokenVersion);

    /** Issues an access token for a specific API. */
    String generateAccessToken(String username, int tokenVersion, TokenType tokenType);

    /** Issues the {@link TokenType#ADMIN_MFA_CHALLENGE} token for the password step of admin login. */
    String generateAdminMfaChallengeToken(String username, String challengeId);

    /** Issues a {@link TokenType#ADMIN} access token carrying a session id as {@code jti}. */
    String generateAdminAccessToken(String username, int tokenVersion, String sessionId);

    /** @return the token's {@code jti}, or null if it carries none. */
    String getTokenId(String token);

    String generateRefreshToken(Authentication authentication);

    String generateRefreshToken(String username);

    String getUsername(String token);

    /** @return the token's version claim, or null for tokens issued before versioning. */
    Integer getTokenVersion(String token);

    /** @return which API this token is for. Never null - a token with no {@code typ} claim */
    TokenType getTokenType(String token);

    boolean validateToken(String token);
}
