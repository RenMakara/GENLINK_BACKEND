package kh.edu.acleda.campuslink.modules.admin.auth.totp;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/** ADM-022 AC#3 and AC#4. */
class RecoveryCodeServiceTest {

    /** Strength 4: ten hashes at the production cost would make this suite crawl. */
    private final RecoveryCodeService service =
        new RecoveryCodeService(new BCryptPasswordEncoder(4), new ObjectMapper());

    @Test
    void generatesTenCodes() {
        assertThat(service.generate().plaintext()).hasSize(RecoveryCodeService.CODE_COUNT);
    }

    @Test
    void codesAreUnique() {
        assertThat(service.generate().plaintext()).doesNotHaveDuplicates();
    }

    @Test
    void twoAdminsDoNotShareCodes() {
        assertThat(service.generate().plaintext())
            .doesNotContainAnyElementsOf(service.generate().plaintext());
    }

    /** AC#3 - the plaintext must not be recoverable from what is persisted. */
    @Test
    void storedJsonContainsNoPlaintext() {
        RecoveryCodeService.GeneratedCodes codes = service.generate();

        for (String code : codes.plaintext()) {
            assertThat(codes.json()).doesNotContain(code);
            assertThat(codes.json()).doesNotContain(code.replace("-", ""));
        }
        assertThat(codes.json()).contains("hash").contains("usedAt");
    }

    /**
     * AC#4, the one that justifies JSONB over a single hash column: spending one code must
     * leave the other nine usable. A concatenated column could only invalidate all or none.
     */
    @Test
    void consumingOneCodeLeavesTheOtherNineValid() {
        RecoveryCodeService.GeneratedCodes codes = service.generate();
        List<String> plaintext = codes.plaintext();

        String updated = service.consume(codes.json(), plaintext.get(3)).orElseThrow();

        assertThat(service.remaining(updated)).isEqualTo(9);
        for (int i = 0; i < plaintext.size(); i++) {
            boolean stillValid = service.consume(updated, plaintext.get(i)).isPresent();
            assertThat(stillValid)
                .as("code %d should be %s", i, i == 3 ? "spent" : "valid")
                .isEqualTo(i != 3);
        }
    }

    @Test
    void aCodeCannotBeUsedTwice() {
        RecoveryCodeService.GeneratedCodes codes = service.generate();
        String code = codes.plaintext().get(0);

        String afterFirst = service.consume(codes.json(), code).orElseThrow();

        assertThat(service.consume(afterFirst, code)).isEmpty();
        assertThat(service.remaining(afterFirst)).isEqualTo(9);
    }

    /** A rejected attempt must not quietly spend something. */
    @Test
    void aWrongCodeChangesNothing() {
        RecoveryCodeService.GeneratedCodes codes = service.generate();

        assertThat(service.consume(codes.json(), "ZZZZZ-ZZZZZ")).isEmpty();
        assertThat(service.remaining(codes.json())).isEqualTo(10);
    }

    /** Read off paper and typed under pressure: dashes, spacing and case must not matter. */
    @Test
    void acceptsTheCodeHoweverItIsTyped() {
        RecoveryCodeService.GeneratedCodes codes = service.generate();
        String code = codes.plaintext().get(0);

        assertThat(service.consume(codes.json(), code.toLowerCase())).isPresent();
        assertThat(service.consume(codes.json(), code.replace("-", ""))).isPresent();
        assertThat(service.consume(codes.json(), "  " + code + " ")).isPresent();
    }

    @Test
    void formatsCodesForReadingAloud() {
        assertThat(service.generate().plaintext())
            .allMatch(code -> code.matches("[ABCDEFGHJKMNPQRSTVWXYZ0-9]{5}-[ABCDEFGHJKMNPQRSTVWXYZ0-9]{5}"));
    }

    /**
     * I, L, O and U are absent on purpose: these are transcribed by hand by someone already
     * locked out, and 0/O or 1/I confusion costs an attempt.
     */
    @Test
    void omitsConfusableCharacters() {
        String allCodes = String.join("", service.generate().plaintext());
        assertThat(allCodes).doesNotContain("I").doesNotContain("L").doesNotContain("O").doesNotContain("U");
    }

    @Test
    void handlesMissingOrEmptyStoredCodes() {
        assertThat(service.consume(null, "ABCDE-FGHJK")).isEmpty();
        assertThat(service.consume("", "ABCDE-FGHJK")).isEmpty();
        assertThat(service.remaining(null)).isZero();
    }

    @Test
    void ignoresMalformedInputWithoutThrowing() {
        RecoveryCodeService.GeneratedCodes codes = service.generate();
        assertThat(service.consume(codes.json(), null)).isEmpty();
        assertThat(service.consume(codes.json(), "short")).isEmpty();
    }

    /** Round-trips through the JSONB shape the column actually stores. */
    @Test
    void survivesAPersistenceRoundTrip() {
        RecoveryCodeService.GeneratedCodes codes = service.generate();
        String reloaded = new String(codes.json().getBytes());

        Optional<String> consumed = service.consume(reloaded, codes.plaintext().get(5));
        assertThat(consumed).isPresent();
    }
}
