package kh.edu.acleda.campuslink.modules.admin.auth.totp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** The ten single-use codes an admin falls back on when their phone is lost (SSOT §10.8). */
@Service
public class RecoveryCodeService {

    public static final int CODE_COUNT = 10;

    /** Characters per code, before the display dash. 10 x 5 bits = 50 bits of entropy. */
    private static final int CODE_LENGTH = 10;

    /** Crockford-style alphabet with {@code I}, {@code L}, {@code O} and {@code U} removed. */
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ0123456789";

    private static final TypeReference<List<StoredCode>> CODE_LIST = new TypeReference<>() {
    };

    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    public RecoveryCodeService(PasswordEncoder passwordEncoder, ObjectMapper objectMapper) {
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    /** One stored code. */
    public record StoredCode(String hash, String usedAt) {

        boolean unused() {
            return usedAt == null;
        }
    }

    /** Plaintext codes for the admin, plus the JSONB to persist. Pair them; never store the former. */
    public record GeneratedCodes(List<String> plaintext, String json) {
    }

    /** Ten fresh codes. */
    public GeneratedCodes generate() {
        List<String> plaintext = new ArrayList<>(CODE_COUNT);
        List<StoredCode> stored = new ArrayList<>(CODE_COUNT);

        for (int i = 0; i < CODE_COUNT; i++) {
            String code = randomCode();
            plaintext.add(format(code));
            stored.add(new StoredCode(passwordEncoder.encode(code), null));
        }
        return new GeneratedCodes(List.copyOf(plaintext), toJson(stored));
    }

    /** Spends one code. */
    public Optional<String> consume(String storedJson, String candidate) {
        String normalised = normalise(candidate);
        if (normalised.length() != CODE_LENGTH) {
            return Optional.empty();
        }

        List<StoredCode> codes = fromJson(storedJson);
        int matchedIndex = -1;
        for (int i = 0; i < codes.size(); i++) {
            StoredCode code = codes.get(i);
            boolean matches = passwordEncoder.matches(normalised, code.hash());
            if (matches && code.unused() && matchedIndex < 0) {
                matchedIndex = i;
            }
        }
        if (matchedIndex < 0) {
            return Optional.empty();
        }

        List<StoredCode> updated = new ArrayList<>(codes);
        updated.set(matchedIndex, new StoredCode(codes.get(matchedIndex).hash(), Instant.now().toString()));
        return Optional.of(toJson(updated));
    }

    /** How many are still spendable - shown in the UI so an admin can regenerate before running out. */
    public long remaining(String storedJson) {
        return fromJson(storedJson).stream().filter(StoredCode::unused).count();
    }

    private String randomCode() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }

    /** {@code ABCDE-FGHJK}. The dash is presentation only and is stripped on the way back in. */
    private static String format(String code) {
        return code.substring(0, 5) + "-" + code.substring(5);
    }

    private static String normalise(String candidate) {
        return candidate == null ? "" : candidate.trim().replace("-", "").replace(" ", "").toUpperCase();
    }

    private String toJson(List<StoredCode> codes) {
        try {
            return objectMapper.writeValueAsString(codes);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialise recovery codes", ex);
        }
    }

    private List<StoredCode> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, CODE_LIST);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Stored recovery codes are not readable", ex);
        }
    }
}
