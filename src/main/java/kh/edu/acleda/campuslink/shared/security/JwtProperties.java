package kh.edu.acleda.campuslink.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String issuer;
    private String secret;
    private long accessTokenExpirationMs;
    /**
     * Admin access token lifetime. 10 minutes, deliberately shorter than the mobile token's
     * 15: an admin session reaches every user's data, so the window in which a stolen one
     * stays usable should be smaller.
     *
     * <p>Defaulted in code so the admin portal works without adding configuration to every
     * environment and every test profile.
     */
    private long adminAccessTokenExpirationMs = 600_000L;
    /**
     * Lifetime of the MFA challenge token issued by the admin password step (SSOT §10.8).
     *
     * <p>Five minutes: long enough to open an authenticator app and type six digits, short
     * enough that a challenge captured from a log or a proxy is worthless by the time anyone
     * looks at it. Defaulted in code for the same reason as the admin access token above.
     */
    private long adminMfaChallengeExpirationMs = 300_000L;

    /**
     * Admin refresh token lifetime: 8 hours, against the mobile token's 7 days.
     *
     * <p>Roughly a working day, so an admin signs in once each morning rather than holding a
     * session that survives a fortnight of weekends. A stolen mobile refresh token reaches one
     * person's data; a stolen admin one reaches everyone's.
     */
    private long adminRefreshTokenExpirationMs = 28_800_000L;

    private long refreshTokenExpirationMs;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }

    public void setAccessTokenExpirationMs(long accessTokenExpirationMs) {
        this.accessTokenExpirationMs = accessTokenExpirationMs;
    }

    public long getAdminAccessTokenExpirationMs() {
        return adminAccessTokenExpirationMs;
    }

    public void setAdminAccessTokenExpirationMs(long adminAccessTokenExpirationMs) {
        this.adminAccessTokenExpirationMs = adminAccessTokenExpirationMs;
    }

    public long getAdminMfaChallengeExpirationMs() {
        return adminMfaChallengeExpirationMs;
    }

    public void setAdminMfaChallengeExpirationMs(long adminMfaChallengeExpirationMs) {
        this.adminMfaChallengeExpirationMs = adminMfaChallengeExpirationMs;
    }

    public long getAdminRefreshTokenExpirationMs() {
        return adminRefreshTokenExpirationMs;
    }

    public void setAdminRefreshTokenExpirationMs(long adminRefreshTokenExpirationMs) {
        this.adminRefreshTokenExpirationMs = adminRefreshTokenExpirationMs;
    }

    public long getRefreshTokenExpirationMs() {
        return refreshTokenExpirationMs;
    }

    public void setRefreshTokenExpirationMs(long refreshTokenExpirationMs) {
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }
}
