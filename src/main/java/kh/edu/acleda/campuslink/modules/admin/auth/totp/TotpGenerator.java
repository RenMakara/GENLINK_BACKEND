package kh.edu.acleda.campuslink.modules.admin.auth.totp;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** RFC 6238 TOTP: 30-second step, 6 digits, HMAC-SHA1, ±1 window of skew (SSOT §10.8). */
@Component
public class TotpGenerator {

    /** Seconds per code. */
    public static final int STEP_SECONDS = 30;
    public static final int DIGITS = 6;

    /** How many steps either side of "now" are accepted. */
    public static final int SKEW_STEPS = 1;

    /** 160 bits, matching the HMAC-SHA1 block the RFC recommends. */
    private static final int SECRET_BYTES = 20;

    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int[] POWERS_OF_TEN = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000};

    private final SecureRandom random = new SecureRandom();

    /** A fresh shared secret, raw bytes. Base32-encode it before showing it to anyone. */
    public byte[] generateSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        random.nextBytes(secret);
        return secret;
    }

    /** The code for a given instant. Package-visible timing so tests can pin RFC vectors. */
    public String generate(byte[] secret, Instant at) {
        return generateForCounter(secret, at.getEpochSecond() / STEP_SECONDS);
    }

    /** Whether {@code candidate} is valid now, allowing {@link #SKEW_STEPS} either side. */
    public boolean verify(byte[] secret, String candidate, Instant now) {
        if (candidate == null || candidate.length() != DIGITS) {
            return false;
        }
        long counter = now.getEpochSecond() / STEP_SECONDS;
        boolean matched = false;
        for (int offset = -SKEW_STEPS; offset <= SKEW_STEPS; offset++) {
            // No early break: every window is evaluated.
            matched |= constantTimeEquals(generateForCounter(secret, counter + offset), candidate);
        }
        return matched;
    }

    private String generateForCounter(byte[] secret, long counter) {
        byte[] hash = hmac(secret, ByteBuffer.allocate(Long.BYTES).putLong(counter).array());

        // RFC 4226 dynamic truncation: the low nibble of the last byte picks the offset.
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
            | ((hash[offset + 1] & 0xFF) << 16)
            | ((hash[offset + 2] & 0xFF) << 8)
            | (hash[offset + 3] & 0xFF);

        return String.format("%0" + DIGITS + "d", binary % POWERS_OF_TEN[DIGITS]);
    }

    private static byte[] hmac(byte[] secret, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(message);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            // HmacSHA1 is required of every JDK; if it is missing the platform is broken.
            throw new IllegalStateException("HmacSHA1 is unavailable", ex);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected.length() != actual.length()) {
            return false;
        }
        int difference = 0;
        for (int i = 0; i < expected.length(); i++) {
            difference |= expected.charAt(i) ^ actual.charAt(i);
        }
        return difference == 0;
    }
}
