package kh.edu.acleda.campuslink.modules.admin.auth.totp;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pinned to <strong>RFC 6238 Appendix B</strong>, not to itself.
 *
 * <p>A TOTP implementation that only agrees with its own expectations is worthless: the whole
 * point is to agree with Google Authenticator, Aegis, 1Password and every other app the team
 * might use. Matching the RFC's published vectors is the closest thing to testing against all
 * of them at once.
 */
class TotpGeneratorTest {

    /** The RFC's SHA1 seed: the ASCII string "12345678901234567890". */
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    private final TotpGenerator generator = new TotpGenerator();

    /**
     * Appendix B publishes 8-digit values; these are their last six digits.
     *
     * <p>That truncation is exact rather than approximate: the 8-digit code is
     * {@code binary % 10^8} and the 6-digit code is {@code binary % 10^6}, so the shorter one
     * is always the tail of the longer. The dynamic-truncation step being tested is identical
     * either way, so a bug in it still fails here.
     */
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
        // 1234567890 -> 005924 above. A naive Integer.toString would render "5924" and every
        // app would reject it, so the zero-padding is load-bearing rather than cosmetic.
        assertThat(generator.generate(RFC_SECRET, Instant.ofEpochSecond(1234567890)))
            .hasSize(6)
            .startsWith("00");
    }

    @Test
    void acceptsTheCurrentCode() {
        Instant now = Instant.ofEpochSecond(1111111111);
        assertThat(generator.verify(RFC_SECRET, generator.generate(RFC_SECRET, now), now)).isTrue();
    }

    /**
     * ±1 step of tolerance (SSOT §10.8). A phone's clock drifts, and a code typed at the very
     * end of its window arrives inside the next one; without this those look to the admin like
     * a broken authenticator.
     */
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
