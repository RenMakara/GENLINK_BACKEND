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

    // ---------------------------------------------------------------------------------
    // Admin access token with a session id (ADM-024)
    // ---------------------------------------------------------------------------------

    /**
     * The claim that confines this token to {@code /api/v1/admin/**}.
     *
     * <p>Pinned here rather than only at the call site: {@code generateAdminAccessToken} is the
     * one place that decides it, and a caller can no longer pass the wrong type by mistake.
     */
    @Test
    void adminAccessTokenCarriesTheAdminTypeAndSessionId() {
        profiles("prod");
        JwtTokenProvider provider = new JwtTokenProvider(props(STRONG_SECRET), environment);

        String token = provider.generateAdminAccessToken("admin@genlink.kh", 7, "session-abc");

        org.assertj.core.api.Assertions.assertThat(provider.getTokenType(token)).isEqualTo(TokenType.ADMIN);
        org.assertj.core.api.Assertions.assertThat(provider.getTokenId(token)).isEqualTo("session-abc");
        org.assertj.core.api.Assertions.assertThat(provider.getUsername(token)).isEqualTo("admin@genlink.kh");
    }

    /**
     * The version claim must survive: without it {@code isTokenVersionCurrent} fails closed and
     * every admin token would be rejected on the next request.
     */
    @Test
    void adminAccessTokenKeepsTheTokenVersion() {
        profiles("prod");
        JwtTokenProvider provider = new JwtTokenProvider(props(STRONG_SECRET), environment);

        String token = provider.generateAdminAccessToken("admin@genlink.kh", 7, "session-abc");

        org.assertj.core.api.Assertions.assertThat(provider.getTokenVersion(token)).isEqualTo(7);
    }

    /** Two sign-ins must not share a session id, or logging out of one would end both. */
    @Test
    void adminAccessTokensWithDifferentSessionIdsStayDistinct() {
        profiles("prod");
        JwtTokenProvider provider = new JwtTokenProvider(props(STRONG_SECRET), environment);

        String first = provider.generateAdminAccessToken("admin@genlink.kh", 7, "session-1");
        String second = provider.generateAdminAccessToken("admin@genlink.kh", 7, "session-2");

        org.assertj.core.api.Assertions.assertThat(provider.getTokenId(first)).isEqualTo("session-1");
        org.assertj.core.api.Assertions.assertThat(provider.getTokenId(second)).isEqualTo("session-2");
    }

    // ---------------------------------------------------------------------------------
    // MFA challenge token (ADM-021)
    // ---------------------------------------------------------------------------------

    @Test
    void challengeTokenCarriesTheChallengeTypeAndNotAnAccessType() {
        profiles("prod");
        JwtTokenProvider provider = new JwtTokenProvider(props(STRONG_SECRET), environment);

        String token = provider.generateAdminMfaChallengeToken("admin@genlink.kh", "challenge-1");

        // The claim is the entire access-control story for this token: the filter compares it
        // against what the route requires, and no route requires this value.
        org.assertj.core.api.Assertions.assertThat(provider.getTokenType(token))
            .isEqualTo(TokenType.ADMIN_MFA_CHALLENGE);
        org.assertj.core.api.Assertions.assertThat(provider.getUsername(token))
            .isEqualTo("admin@genlink.kh");
        org.assertj.core.api.Assertions.assertThat(provider.getTokenId(token)).isEqualTo("challenge-1");
    }

    /**
     * No {@code tv} claim. Token version is what makes an access token revocable; carrying one
     * here would imply this token is a session, and {@code isTokenVersionCurrent} fails closed
     * on a null version, so its absence also means the filter could never accept it even if
     * the type check were removed.
     */
    @Test
    void challengeTokenCarriesNoTokenVersion() {
        profiles("prod");
        JwtTokenProvider provider = new JwtTokenProvider(props(STRONG_SECRET), environment);

        String token = provider.generateAdminMfaChallengeToken("admin@genlink.kh", "challenge-1");

        org.assertj.core.api.Assertions.assertThat(provider.getTokenVersion(token)).isNull();
    }

    @Test
    void challengeTokenExpiresInFiveMinutesByDefault() {
        profiles("prod");
        JwtProperties properties = props(STRONG_SECRET);
        // Not set by props(): the default is the contract, so that no environment has to
        // configure it and none can quietly lengthen the window by omission.
        org.assertj.core.api.Assertions.assertThat(properties.getAdminMfaChallengeExpirationMs())
            .isEqualTo(300_000L);

        JwtTokenProvider provider = new JwtTokenProvider(properties, environment);
        org.assertj.core.api.Assertions.assertThat(
            provider.validateToken(provider.generateAdminMfaChallengeToken("a@b.c", "id"))).isTrue();
    }
}
