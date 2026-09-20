package kh.edu.acleda.campuslink.shared.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/*  Authenticated encryption សម្រាប់ credential columns (SSOT §9.2).
 GCM គឺ authenticate ព្រមទាំង encrypt ដូច្នេះ tampered ciphertext នឹង
 fail loud ជំនួស​ឱ្យ​ការ decrypt ទៅ​ជា plausible garbage។ ចំពោះ CBC
 អ្នកដែលមាន write access អាច flip bits និង change stored secret
 ទៅ​ជា​របស់​ខ្លួន​ឯងដោយ silent បាន
 Recovery codes គឺត្រូវបាន hashed ព្រោះវាគ្រាន់តែត្រូវ check តែប៉ុណ្ណោះ។ ចំណែក
 TOTP secret ត្រូវតែ read back ដើម្បី generate codes ដូច្នេះវាត្រូវ encrypted។
 ការបែងចែកនេះគឺសំខាន់ណាស់៖ hashed secret នឹងធ្វើឱ្យ authenticator app 
 ទាំងអស់ មិនអាចប្រើប្រាស់បាន។ */
@Component
public class FieldEncryptor {

    private static final Logger log = LoggerFactory.getLogger(FieldEncryptor.class);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ALGORITHM = "AES";
    private static final int KEY_BYTES = 32;
    /** 96 bits is the size GCM is specified and optimised for. */
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    /**
     * Marks the key generation that produced a value. Nothing rotates keys yet, but
     * stored
     * ciphertext outlives code: without a marker, a future rotation cannot tell
     * which values
     * still need the old key, and the only remedy would be re-enrolling every
     * administrator.
     */
    private static final String PREFIX = "v1:";

    private static final Set<String> DEV_PROFILES = Set.of("dev", "local", "test");

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public FieldEncryptor(CryptoProperties properties, Environment environment) {
        this.key = resolveKey(properties, environment);
    }

    /**
     * Mirrors {@code JwtTokenProvider}: configured key everywhere, ephemeral
     * fallback only
     * under a dev profile so a local checkout runs with no setup.
     *
     * <p>
     * The fallback is louder here than it is for JWT, and deliberately so. An
     * ephemeral JWT
     * key signs everyone out on restart, which is an inconvenience. An ephemeral
     * encryption key
     * makes every stored TOTP secret permanently undecryptable, which locks
     * administrators out
     * of their own accounts.
     */
    private static SecretKey resolveKey(CryptoProperties properties, Environment environment) {
        String configured = properties.getSecretKey();
        if (configured != null && !configured.isBlank()) {
            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(configured.trim());
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException(
                        "app.crypto.secret-key is not valid Base64. Generate one with "
                                + "`openssl rand -base64 32`.",
                        ex);
            }
            if (decoded.length != KEY_BYTES) {
                throw new IllegalStateException(
                        "app.crypto.secret-key must decode to exactly " + KEY_BYTES + " bytes for "
                                + "AES-256; got " + decoded.length + ". Generate one with "
                                + "`openssl rand -base64 32`.");
            }
            return new SecretKeySpec(decoded, ALGORITHM);
        }

        if (isDevProfile(environment)) {
            byte[] ephemeral = new byte[KEY_BYTES];
            new SecureRandom().nextBytes(ephemeral);
            log.warn("app.crypto.secret-key is not set - generated an EPHEMERAL key. Anything "
                    + "encrypted with it becomes unreadable on restart, so admins enrolled in TOTP "
                    + "now will have to enrol again. Set app.crypto.secret-key for anything but a "
                    + "throwaway local run.");
            return new SecretKeySpec(ephemeral, ALGORITHM);
        }

        throw new IllegalStateException(
                "app.crypto.secret-key must be set (Base64, 32 bytes) via the ADMIN_CRYPTO_KEY env "
                        + "var, e.g. `openssl rand -base64 32`. Auto-generation only happens under a "
                        + "dev/local/test profile.");
    }

    private static boolean isDevProfile(Environment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if (DEV_PROFILES.contains(profile.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return {@code v1:<base64(iv || ciphertext || tag)>}, or null for null input.
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            // A fresh IV per call. Reusing one under GCM is catastrophic - it leaks the XOR
            // of
            // two plaintexts and, worse, allows forgery of further ciphertexts.
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException ex) {
            // No plaintext in the message: this is a credential.
            throw new IllegalStateException("Failed to encrypt a credential field", ex);
        }
    }

    /**
     * @throws IllegalStateException if the value was written with a different key
     *                               or has been
     *                               tampered with. Both are worth failing on rather
     *                               than papering over - a silent null
     *                               would present as "this admin has no second
     *                               factor".
     */
    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            throw new IllegalStateException(
                    "Encrypted field is missing the " + PREFIX + " marker. It was probably written "
                            + "before encryption was introduced, or by a different codebase.");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_BYTES);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(combined, IV_BYTES, combined.length - IV_BYTES);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException
                | ArrayIndexOutOfBoundsException ex) {
            throw new IllegalStateException(
                    "Failed to decrypt a credential field. The encryption key has probably changed; "
                            + "affected admins must re-enrol.",
                    ex);
        }
    }
}
