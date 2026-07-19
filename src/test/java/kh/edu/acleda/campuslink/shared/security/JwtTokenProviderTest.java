package kh.edu.acleda.campuslink.shared.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtTokenProviderTest {

    private static final String STRONG_SECRET = "0123456789abcdef0123456789abcdef"; // 32 chars

    @Mock private Environment environment;

    private JwtProperties props(String secret) {
        JwtProperties p = new JwtProperties();
        p.setSecret(secret);
        p.setIssuer("genlink");
        p.setAccessTokenExpirationMs(60000);
        p.setRefreshTokenExpirationMs(120000);
        return p;
    }

    private void profiles(String... active) {
        when(environment.getActiveProfiles()).thenReturn(active);
    }

    @Test
    void aStrongSecretIsAccepted() {
        profiles("prod");
        assertThatCode(() -> new JwtTokenProvider(props(STRONG_SECRET), environment)).doesNotThrowAnyException();
    }

    @Test
    void missingSecretUnderADevProfileAutoGenerates() {
        profiles("dev");
        assertThatCode(() -> new JwtTokenProvider(props(null), environment)).doesNotThrowAnyException();
    }

    @Test
    void missingSecretWithoutADevProfileFailsFast() {
        profiles("prod");
        assertThatThrownBy(() -> new JwtTokenProvider(props(" "), environment))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("app.jwt.secret must be set");
    }

    @Test
    void missingSecretWithNoActiveProfileFailsFast() {
        profiles(); // no profile -> treated as non-dev, must fail
        assertThatThrownBy(() -> new JwtTokenProvider(props(null), environment))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aTooShortSecretFailsFast() {
        profiles("dev");
        assertThatThrownBy(() -> new JwtTokenProvider(props("short"), environment))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("at least");
    }
}
