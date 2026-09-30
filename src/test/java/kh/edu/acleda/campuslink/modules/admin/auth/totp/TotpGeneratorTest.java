package kh.edu.acleda.campuslink.modules.admin.auth.totp;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Pinned to RFC 6238 Appendix B , not to itself. */
class TotpGeneratorTest {

    /** The RFC's SHA1 seed: the ASCII string "12345678901234567890". */
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    private final TotpGenerator generator = new TotpGenerator();

    /** Appendix B publishes 8-digit values; these are their last six digits. */
    @ParameterizedTest(name = "t={0}s -> {1}")
    @CsvSource({
        "59,          287082",
        "1111111109,  081804",
        "1111111111,  050471",
        "1234567890,  005924",
        "2000000000,  279037",
        "20000000000, 353130"
    })
    void matchesRfc6238TestVectors(long epochSecond, String expected) {
        assertThat(generator.generate(RFC_SECRET, Instant.ofEpochSecond(epochSecond)))
            .isEqualTo(expected);
    }

    @Test
    void producesSixDigitsIncludingLeadingZeros() {
        // 1234567890 -> 005924 above.
        assertThat(generator.generate(RFC_SECRET, Instant.ofEpochSecond(1234567890)))
            .hasSize(6)
            .startsWith("00");
    }

    @Test
    void acceptsTheCurrentCode() {
        Instant now = Instant.ofEpochSecond(1111111111);
        assertThat(generator.verify(RFC_SECRET, generator.generate(RFC_SECRET, now), now)).isTrue();
    }

    /** ±1 step of tolerance (SSOT §10.8). */
    @Test
    void acceptsTheCodeFromOneStepEitherSide() {
        Instant now = Instant.ofEpochSecond(1111111111);
        Instant previous = now.minusSeconds(TotpGenerator.STEP_SECONDS);
        Instant next = now.plusSeconds(TotpGenerator.STEP_SECONDS);

        assertThat(generator.verify(RFC_SECRET, generator.generate(RFC_SECRET, previous), now)).isTrue();
        assertThat(generator.verify(RFC_SECRET, generator.generate(RFC_SECRET, next), now)).isTrue();
    }

    /** Tolerance has to stop somewhere, or an old code stays usable indefinitely. */
    @Test
    void rejectsACodeFromTwoStepsAway() {
        Instant now = Instant.ofEpochSecond(1111111111);
        String stale = generator.generate(RFC_SECRET, now.minusSeconds(2L * TotpGenerator.STEP_SECONDS));

        assertThat(generator.verify(RFC_SECRET, stale, now)).isFalse();
    }

    @Test
    void rejectsAWrongCode() {
        assertThat(generator.verify(RFC_SECRET, "000000", Instant.ofEpochSecond(1111111111))).isFalse();
    }

    @Test
    void rejectsMalformedInputRatherThanThrowing() {
        Instant now = Instant.ofEpochSecond(1111111111);
        assertThat(generator.verify(RFC_SECRET, null, now)).isFalse();
        assertThat(generator.verify(RFC_SECRET, "", now)).isFalse();
        assertThat(generator.verify(RFC_SECRET, "12345", now)).isFalse();
        assertThat(generator.verify(RFC_SECRET, "1234567", now)).isFalse();
    }

    @Test
    void generatesA160BitSecret() {
        // Matches the HMAC-SHA1 block size the RFC recommends.
        assertThat(generator.generateSecret()).hasSize(20);
    }

    @Test
    void generatesADifferentSecretEveryTime() {
        assertThat(generator.generateSecret()).isNotEqualTo(generator.generateSecret());
    }

    /** A different secret must not validate another's code - the obvious property, unstated is unchecked. */
    @Test
    void doesNotAcceptACodeFromADifferentSecret() {
        Instant now = Instant.ofEpochSecond(1111111111);
        byte[] other = "09876543210987654321".getBytes(StandardCharsets.US_ASCII);

        assertThat(generator.verify(other, generator.generate(RFC_SECRET, now), now)).isFalse();
    }
}
