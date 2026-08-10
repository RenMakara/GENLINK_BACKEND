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

/**
 * The ten single-use codes an admin falls back on when their phone is lost (SSOT §10.8).
 *
 * <h2>Hashed, not encrypted</h2>
 *
 * The opposite decision from {@code totp_secret}. A recovery code is only ever *checked*, never
 * read back, so it is stored as a BCrypt hash and the plaintext exists exactly once - in the
 * response to {@code /mfa/confirm}. An admin who loses the printout regenerates the set; nobody,
 * including someone holding the database, can recover the originals.
 *
 * <h2>Why JSONB and not a column</h2>
 *
 * The codes are individually consumable. A single concatenated hash column could not record
 * which of the ten had been spent, so spending one would have to invalidate all ten or none.
 */
@Service
public class RecoveryCodeService {

    public static final int CODE_COUNT = 10;

    /** Characters per code, before the display dash. 10 x 5 bits = 50 bits of entropy. */
    private static final int CODE_LENGTH = 10;

    /**
     * Crockford-style alphabet with {@code I}, {@code L}, {@code O} and {@code U} removed.
     *
     * <p>These are read off paper and typed under pressure, usually by someone already locked
     * out. Confusing 0/O or 1/I costs an attempt, and attempts are limited.
     */
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

    /**
     * One stored code. {@code usedAt} is null until it is spent.
     *
     * <p>{@code usedAt} is an ISO-8601 <strong>string</strong>, not an {@code Instant}, on
     * purpose. This record is a storage format: it is what sits in the JSONB column and has to
     * stay readable for as long as the rows do. Serialising an {@code Instant} would make that
     * shape depend on the application's global Jackson configuration - flipping
     * {@code write-dates-as-timestamps} would silently start writing numbers where the old rows
     * hold strings, and nothing would notice until a recovery code was needed.
     */
    public record StoredCode(String hash, String usedAt) {

        boolean unused() {
            return usedAt == null;
        }
    }

    /** Plaintext codes for the admin, plus the JSONB to persist. Pair them; never store the former. */
    public record GeneratedCodes(List<String> plaintext, String json) {
    }

    /**
     * Ten fresh codes.
     *
     * <p>Costs ten BCrypt hashes - around a second. That is fine: it happens once per admin, at
     * enrolment, and the alternative is a cheaper hash on a credential that grants full access.
     */
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

    /**
     * Spends one code.
     *
     * <p>Every unused code is compared, with no early exit, so the time taken does not reveal
     * how far down the list a match sat - or whether one existed at all.
     *
     * @return the updated JSON when the code was valid and unused, otherwise empty. An empty
     *     result must leave the stored codes untouched.
     */
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
