package kh.edu.acleda.campuslink.modules.admin.auth.totp;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pinned to the RFC 4648 §10 vectors. */
class Base32Test {

    /** RFC 4648 §10, with the trailing '=' padding removed as authenticator apps expect. */
    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
        "f,      MY",
        "fo,     MZXQ",
        "foo,    MZXW6",
        "foob,   MZXW6YQ",
        "fooba,  MZXW6YTB",
        "foobar, MZXW6YTBOI"
    })
    void encodesTheRfc4648Vectors(String input, String expected) {
        assertThat(Base32.encode(input.getBytes(StandardCharsets.US_ASCII))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{1} -> \"{0}\"")
    @CsvSource({
        "f,      MY",
        "fo,     MZXQ",
        "foo,    MZXW6",
        "foob,   MZXW6YQ",
        "fooba,  MZXW6YTB",
        "foobar, MZXW6YTBOI"
    })
    void decodesTheRfc4648Vectors(String expected, String encoded) {
        assertThat(new String(Base32.decode(encoded), StandardCharsets.US_ASCII)).isEqualTo(expected);
    }

    @Test
    void roundTripsA160BitSecret() {
        byte[] secret = new TotpGenerator().generateSecret();
        assertThat(Base32.decode(Base32.encode(secret))).isEqualTo(secret);
    }

    @Test
    void emitsNoPadding() {
        // Apps generally reject a secret containing '=', and the length is already known.
        assertThat(Base32.encode("f".getBytes(StandardCharsets.US_ASCII))).doesNotContain("=");
    }

    /** An admin retyping a secret by hand will not match the casing, and may keep the padding. */
    @Test
    void acceptsLowerCaseAndPaddingOnTheWayBackIn() {
        assertThat(new String(Base32.decode("mzxw6ytboi"), StandardCharsets.US_ASCII)).isEqualTo("foobar");
        assertThat(new String(Base32.decode("MZXW6YQ="), StandardCharsets.US_ASCII)).isEqualTo("foob");
    }

    @Test
    void rejectsCharactersOutsideTheAlphabet() {
        // '1', '8' and '0' are absent from the Base32 alphabet precisely.
        assertThatThrownBy(() -> Base32.decode("MZXW6YTB01"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Base32");
    }
}
