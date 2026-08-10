package kh.edu.acleda.campuslink.shared.security;

import org.springframework.security.core.Authentication;

public interface JwtService {
    /**
     * Issues a {@link TokenType#MOBILE} access token.
     *
     * @param tokenVersion the user's current token version, embedded so the token can
     *                     be invalidated on logout, password change or revoke-all.
     */
    String generateAccessToken(String username, int tokenVersion);

    /**
     * Issues an access token for a specific API.
     *
     * <p>The type decides the lifetime as well as where the token is accepted: admin tokens
     * are shorter-lived, because an admin session reaches every user's data.
     */
    String generateAccessToken(String username, int tokenVersion, TokenType tokenType);

    /**
     * Issues the {@link TokenType#ADMIN_MFA_CHALLENGE} token for the password step of admin
     * login. Grants nothing: it is refused by every route (see {@link TokenType}).
     *
     * @param challengeId a per-attempt identifier, carried as {@code jti}. TOTP verification is
     *     capped at 5 attempts per challenge (SSOT §10.6), which needs something stable to
     *     count against that is not the admin's email - otherwise a fresh login would reset the
     *     counter and the cap would mean nothing.
     */
    String generateAdminMfaChallengeToken(String username, String challengeId);

    /**
     * Issues a {@link TokenType#ADMIN} access token carrying a session id as {@code jti}.
     *
     * <p>Needed because logout must revoke <em>one</em> session. The alternative - bumping
     * {@code token_version} - invalidates every token the person holds, including the mobile
     * app on their phone, which is not what signing out of a browser tab should do.
     */
    String generateAdminAccessToken(String username, int tokenVersion, String sessionId);

    /** @return the token's {@code jti}, or null if it carries none. */
    String getTokenId(String token);

    String generateRefreshToken(Authentication authentication);

    String generateRefreshToken(String username);

    String getUsername(String token);

    /** @return the token's version claim, or null for tokens issued before versioning. */
    Integer getTokenVersion(String token);

    /**
     * @return which API this token is for. Never null - a token with no {@code typ} claim
     *     predates the claim and reads back as {@link TokenType#MOBILE}.
     */
    TokenType getTokenType(String token);

    boolean validateToken(String token);
}
