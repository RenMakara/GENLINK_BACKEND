package kh.edu.acleda.campuslink.shared.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.crypto")
public class CryptoProperties {

    /**
     * Base64-encoded 256-bit key for encrypting credential columns at rest (SSOT §9.2).
     *
     * <p>Supplied by the environment, never committed. Generate with
     * {@code openssl rand -base64 32}.
     *
     * <p><strong>Losing it is not recoverable.</strong> Every encrypted value becomes
     * undecryptable, which for TOTP secrets means every administrator has to re-enrol from a
     * database reset. Back it up wherever {@code JWT_SECRET} is backed up.
     */
    private String secretKey;

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
}
