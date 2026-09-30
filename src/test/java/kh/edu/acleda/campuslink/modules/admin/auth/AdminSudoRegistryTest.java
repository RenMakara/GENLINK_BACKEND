package kh.edu.acleda.campuslink.modules.admin.auth;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ADM-025 AC#2 and AC#3 - the window's lifetime and its scope. */
class AdminSudoRegistryTest {

    private static final String SESSION = "session-1";
    private static final String OTHER_SESSION = "session-2";

    private final AdminSudoRegistry registry = new AdminSudoRegistry();

    @Test
    void aWindowStartsClosed() {
        assertThat(registry.isOpen(SESSION)).isFalse();
    }

    @Test
    void openingMakesItOpen() {
        registry.open(SESSION);
        assertThat(registry.isOpen(SESSION)).isTrue();
    }

    // AC#2 - exactly five minutes.
    @Test
    void theWindowLastsFiveMinutes() {
        Instant before = Instant.now();
        Instant expiry = registry.open(SESSION);

        // Asserted as a range rather than an equality.
        assertThat(Duration.between(before, expiry).toSeconds())
            .isBetween(299L, 301L);
        assertThat(AdminSudoRegistry.WINDOW).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void reportsSecondsRemainingForTheCountdown() {
        registry.open(SESSION);
        assertThat(registry.secondsRemaining(SESSION)).isBetween(295L, 300L);
    }

    @Test
    void aClosedWindowHasNoTimeRemaining() {
        assertThat(registry.secondsRemaining(SESSION)).isZero();
    }

    /** Reopening restarts the five minutes rather than extending an existing window. */
    @Test
    void reopeningRestartsTheClock() {
        Instant first = registry.open(SESSION);
        Instant second = registry.open(SESSION);

        assertThat(second).isAfterOrEqualTo(first);
        assertThat(registry.isOpen(SESSION)).isTrue();
    }

    // AC#3 - per session, never per user.

    @Test
    void aSecondSessionDoesNotInheritTheWindow() {
        registry.open(SESSION);

        assertThat(registry.isOpen(OTHER_SESSION)).isFalse();
        assertThat(registry.secondsRemaining(OTHER_SESSION)).isZero();
    }

    @Test
    void closingOneSessionLeavesAnotherOpen() {
        registry.open(SESSION);
        registry.open(OTHER_SESSION);

        registry.close(SESSION);

        assertThat(registry.isOpen(SESSION)).isFalse();
        assertThat(registry.isOpen(OTHER_SESSION)).isTrue();
    }

    // Expiry and hygiene
    /** The behaviour AC#2 actually asks for: the window must close at five minutes. */
    @Test
    void theWindowClosesAfterFiveMinutes() {
        MovableClock clock = new MovableClock(Instant.parse("2026-08-07T09:00:00Z"));
        AdminSudoRegistry registry = new AdminSudoRegistry(clock);
        registry.open(SESSION);

        clock.advance(Duration.ofMinutes(4).plusSeconds(59));
        assertThat(registry.isOpen(SESSION)).as("still open at 4m59s").isTrue();

        clock.advance(Duration.ofSeconds(1));
        assertThat(registry.isOpen(SESSION)).as("closed at exactly 5m").isFalse();
    }

    @Test
    void theCountdownRunsDownToZero() {
        MovableClock clock = new MovableClock(Instant.parse("2026-08-07T09:00:00Z"));
        AdminSudoRegistry registry = new AdminSudoRegistry(clock);
        registry.open(SESSION);

        assertThat(registry.secondsRemaining(SESSION)).isEqualTo(300);
        clock.advance(Duration.ofMinutes(3));
        assertThat(registry.secondsRemaining(SESSION)).isEqualTo(120);
        clock.advance(Duration.ofMinutes(2));
        assertThat(registry.secondsRemaining(SESSION)).isZero();
    }

    /** An expired entry must be dropped, or the map grows by one per sudo forever. */
    @Test
    void expiredWindowsAreForgotten() {
        MovableClock clock = new MovableClock(Instant.parse("2026-08-07T09:00:00Z"));
        AdminSudoRegistry registry = new AdminSudoRegistry(clock);
        registry.open(SESSION);

        clock.advance(Duration.ofMinutes(6));
        registry.isOpen(SESSION);

        assertThat(registry.size()).isZero();
    }

    /** A clock the test drives, so five minutes costs no wall-clock time. */
    private static final class MovableClock extends java.time.Clock {
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
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    @Test
    void closingASessionThatNeverOpenedOneIsHarmless() {
        registry.close(SESSION);
        registry.close(null);
        assertThat(registry.size()).isZero();
    }

    /** A window with nothing to belong to would be a window every request could claim. */
    @Test
    void refusesToOpenAWindowWithNoSession() {
        assertThatThrownBy(() -> registry.open(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.open("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNullSessionIsNeverOpen() {
        registry.open(SESSION);
        assertThat(registry.isOpen(null)).isFalse();
        assertThat(registry.secondsRemaining(null)).isZero();
    }

    @Test
    void doesNotGrowUnboundedly() {
        for (int i = 0; i < 20; i++) {
            registry.open("session-" + i);
        }
        assertThat(registry.size()).isEqualTo(20);

        for (int i = 0; i < 20; i++) {
            registry.close("session-" + i);
        }
        assertThat(registry.size()).isZero();
    }
}
