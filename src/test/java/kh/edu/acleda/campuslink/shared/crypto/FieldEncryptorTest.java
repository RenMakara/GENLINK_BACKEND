package kh.edu.acleda.campuslink.shared.crypto;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** ADM-022 AC#2: {@code totp_secret} must be unreadable in a raw table dump. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FieldEncryptorTest {

    private static final String KEY = Base64.getEncoder()
        .encodeToString("test-only-key-32-bytes-exactly!!".getBytes());
    private static final String SECRET = "JBSWY3DPEHPK3PXP";

    @Mock
    private Environment environment;

    private FieldEncryptor encryptorWith(String key) {
        CryptoProperties properties = new CryptoProperties();
        properties.setSecretKey(key);
        return new FieldEncryptor(properties, environment);
    }

    // AC#2
    @Test
    void ciphertextDoesNotContainThePlaintext() {
        String stored = encryptorWith(KEY).encrypt(SECRET);

        assertThat(stored).doesNotContain(SECRET);
        // Also not merely encoded: a Base64 or hex "encryption" would satisfy the check above.
        assertThat(new String(Base64.getDecoder().decode(stored.substring(3)))).doesNotContain(SECRET);
    }

    @Test
    void roundTrips() {
        FieldEncryptor encryptor = encryptorWith(KEY);
        assertThat(encryptor.decrypt(encryptor.encrypt(SECRET))).isEqualTo(SECRET);
    }

    /** A fresh IV per call. */
    @Test
    void encryptingTheSameValueTwiceProducesDifferentCiphertext() {
        FieldEncryptor encryptor = encryptorWith(KEY);
        assertThat(encryptor.encrypt(SECRET)).isNotEqualTo(encryptor.encrypt(SECRET));
    }

    @Test
    void bothCiphertextsStillDecryptToTheSameSecret() {
        FieldEncryptor encryptor = encryptorWith(KEY);
        assertThat(encryptor.decrypt(encryptor.encrypt(SECRET)))
            .isEqualTo(encryptor.decrypt(encryptor.encrypt(SECRET)));
    }

    /** GCM authenticates: a tampered row fails loudly rather than decrypting to garbage. */
    @Test
    void rejectsTamperedCiphertext() {
        String stored = encryptorWith(KEY).encrypt(SECRET);
        byte[] raw = Base64.getDecoder().decode(stored.substring("v1:".length()));

        // Last byte is inside the GCM tag; flipping any bit must invalidate the whole message.
        raw[raw.length - 1] ^= 0x01;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> encryptorWith(KEY).decrypt(tampered))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("re-enrol");
    }

    /** Tampering with the ciphertext body, not just the tag, must fail the same way. */
    @Test
    void rejectsCiphertextWithAnAlteredBody() {
        String stored = encryptorWith(KEY).encrypt(SECRET);
        byte[] raw = Base64.getDecoder().decode(stored.substring("v1:".length()));

        // Byte 12 is the first byte after the 12-byte IV, so this edits the ciphertext itself.
        raw[12] ^= 0x01;

        assertThatThrownBy(() -> encryptorWith(KEY).decrypt("v1:" + Base64.getEncoder().encodeToString(raw)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aDifferentKeyCannotRead() {
        String stored = encryptorWith(KEY).encrypt(SECRET);
        String otherKey = Base64.getEncoder().encodeToString("another-key-32-bytes-exactly!!!!".getBytes());

        assertThatThrownBy(() -> encryptorWith(otherKey).decrypt(stored))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void marksTheKeyGenerationSoFutureRotationIsPossible() {
        assertThat(encryptorWith(KEY).encrypt(SECRET)).startsWith("v1:");
    }

    @Test
    void rejectsAValueWrittenBeforeEncryptionExisted() {
        // A plaintext secret already in the column must fail loudly, not be handed back as if
        // it had been decrypted.
        assertThatThrownBy(() -> encryptorWith(KEY).decrypt(SECRET))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("marker");
    }

    @Test
    void passesNullThrough() {
        FieldEncryptor encryptor = encryptorWith(KEY);
        assertThat(encryptor.encrypt(null)).isNull();
        assertThat(encryptor.decrypt(null)).isNull();
    }

    // Key configuration
    @Test
    void refusesToStartWithoutAKeyOutsideDev() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        assertThatThrownBy(() -> encryptorWith(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ADMIN_CRYPTO_KEY");
    }

    @Test
    void generatesAnEphemeralKeyUnderADevProfile() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        assertThatCode(() -> encryptorWith(null)).doesNotThrowAnyException();
    }

    /** A 16-byte key would silently give AES-128 where the design says AES-256. */
    @Test
    void rejectsAKeyOfTheWrongLength() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        String shortKey = Base64.getEncoder().encodeToString("16-bytes-exactly".getBytes());

        assertThatThrownBy(() -> encryptorWith(shortKey))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("32 bytes");
    }

    @Test
    void rejectsAKeyThatIsNotBase64() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        assertThatThrownBy(() -> encryptorWith("not base64 !!!"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Base64");
    }
}
