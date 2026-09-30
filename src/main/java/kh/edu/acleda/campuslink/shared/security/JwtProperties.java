package kh.edu.acleda.campuslink.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String issuer;
    private String secret;
    private long accessTokenExpirationMs;
    /** Admin access token lifetime. */
    private long adminAccessTokenExpirationMs = 600_000L;
    /** Lifetime of the MFA challenge token issued by the admin password step (SSOT §10.8). */
    private long adminMfaChallengeExpirationMs = 300_000L;

    /** Admin refresh token lifetime: 8 hours, against the mobile token's 7 days. */
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
