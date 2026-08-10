package kh.edu.acleda.campuslink.modules.auth.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADM-027 - {@code ADMIN_LOGIN} rate limiting.
 *
 * <p>The service already existed and was already keyed per action, but had <strong>no
 * tests</strong>. It is the control standing between an unauthenticated endpoint and unlimited
 * password guessing against administrator accounts, so "it looks right" is not enough.
 *
 * <p>Time is driven by a movable clock: the window and the block are both fifteen minutes, and
 * a suite that waited for them would take half an hour and still be flaky.
 */
class AuthRateLimitServiceTest {

    private static final String EMAIL = "admin@genlink.kh";
    private static final String IP = "203.0.113.7";

    private MovableClock clock;
    private AuthRateLimitService service;

    @BeforeEach
    void setUp() {
        clock = new MovableClock(Instant.parse("2026-08-07T09:00:00Z"));
        service = new AuthRateLimitService(clock);
    }

    private void failAdminLogin(int times) {
        for (int i = 0; i < times; i++) {
            service.recordAdminLoginFailure(EMAIL, IP);
        }
    }

    // -------------------------------------------------------------------------------
    // AC#1 - six failed admin logins, then blocked for 15 minutes.
    // -------------------------------------------------------------------------------

    @Test
    void fiveFailuresAreToleratedThenTheSixthFindsItBlocked() {
        failAdminLogin(4);
        assertThat(service.isAdminLoginBlocked(EMAIL, IP)).as("after 4 failures").isFalse();

        failAdminLogin(1);
        assertThat(service.isAdminLoginBlocked(EMAIL, IP)).as("after the 5th failure").isTrue();
    }

    @Test
    void theBlockLastsFifteenMinutes() {
        failAdminLogin(5);

        clock.advance(Duration.ofMinutes(14).plusSeconds(59));
        assertThat(service.isAdminLoginBlocked(EMAIL, IP)).as("at 14m59s").isTrue();

        clock.advance(Duration.ofSeconds(1));
        assertThat(service.isAdminLoginBlocked(EMAIL, IP)).as("at exactly 15m").isFalse();
    }

    /**
     * Failures older than the 15-minute window do not accumulate, or a single mistyped password
     * once a fortnight would eventually lock someone out.
     */
    @Test
    void failuresOutsideTheWindowDoNotAccumulate() {
        failAdminLogin(4);

        clock.advance(Duration.ofMinutes(16));
        failAdminLogin(4);

        assertThat(service.isAdminLoginBlocked(EMAIL, IP)).isFalse();
    }

    @Test
    void aSuccessfulLoginClearsTheCounter() {
        failAdminLogin(4);
        service.clearAdminLoginFailures(EMAIL, IP);

        failAdminLogin(4);

        assertThat(service.isAdminLoginBlocked(EMAIL, IP)).isFalse();
    }

    // -------------------------------------------------------------------------------
    // AC#2 - admin failures leave the mobile bucket alone.
    //
    // Sharing them would let anyone lock an administrator out of the portal by failing five
    // MOBILE logins against their address - a denial of service against the people who
    // respond to incidents, from an unauthenticated endpoint.
    // -------------------------------------------------------------------------------

    @Test
    void adminFailuresDoNotBlockMobileLoginForTheSameEmail() {
        failAdminLogin(5);
        assertThat(service.isAdminLoginBlocked(EMAIL, IP)).isTrue();

        assertThatCode(() -> service.checkLoginAllowed(EMAIL, IP))
            .as("the mobile bucket must be untouched")
            .doesNotThrowAnyException();
    }

    @Test
    void mobileFailuresDoNotBlockAdminLoginForTheSameEmail() {
        for (int i = 0; i < 5; i++) {
            service.recordLoginFailure(EMAIL, IP);
        }
        assertThatThrownBy(() -> service.checkLoginAllowed(EMAIL, IP)).isInstanceOf(ApiException.class);

        assertThat(service.isAdminLoginBlocked(EMAIL, IP))
            .as("mobile guessing must not lock an admin out of the portal")
            .isFalse();
    }

    /** The same separation that already protected change-password from login. */
    @Test
    void everyActionKeepsItsOwnBucket() {
        failAdminLogin(5);

        assertThatCode(() -> service.checkPasswordChangeAllowed(EMAIL, IP)).doesNotThrowAnyException();
        assertThatCode(() -> service.checkAccountDeleteAllowed(EMAIL, IP)).doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------------
    // Keying
    // -------------------------------------------------------------------------------

    @Test
    void adifferentEmailIsADifferentBucket() {
        failAdminLogin(5);
        assertThat(service.isAdminLoginBlocked("someone.else@genlink.kh", IP)).isFalse();
    }

    @Test
    void aDifferentAddressIsADifferentBucket() {
        failAdminLogin(5);
        assertThat(service.isAdminLoginBlocked(EMAIL, "198.51.100.4")).isFalse();
    }

    /**
     * Capitalisation must not open a fresh bucket, or the limit is bypassed by typing
     * {@code Admin@...} instead of {@code admin@...}.
     */
    @Test
    void theEmailKeyIsCaseInsensitive() {
        failAdminLogin(5);

        assertThat(service.isAdminLoginBlocked("ADMIN@GENLINK.KH", IP)).isTrue();
        assertThat(service.isAdminLoginBlocked("  admin@genlink.kh  ", IP)).isTrue();
    }

    /**
     * A null address still has to land somewhere. Bucketing it under "unknown" means malformed
     * requests are throttled together instead of each getting a free pass.
     */
    @Test
    void nullsAreBucketedRatherThanExempt() {
        for (int i = 0; i < 5; i++) {
            service.recordAdminLoginFailure(null, null);
        }
        assertThat(service.isAdminLoginBlocked(null, null)).isTrue();
    }

    @Test
    void anUntouchedBucketIsNotBlocked() {
        assertThat(service.isAdminLoginBlocked(EMAIL, IP)).isFalse();
    }

    /** A clock the test drives, so fifteen minutes costs no wall-clock time. */
    private static final class MovableClock extends Clock {
        private Instant now;

        private MovableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
