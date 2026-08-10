package kh.edu.acleda.campuslink.modules.admin.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Open sudo windows, keyed by session (SSOT §10.7).
 *
 * <h2>Per session, not per admin</h2>
 *
 * The key is the access token's session id, so re-authenticating in one browser does not
 * silently arm destructive actions in another tab, on a second device, or on the laptop
 * somebody walked away from. A per-user window would do exactly that, and it is the scenario
 * the control exists for - which is why ADM-025 AC#3 states it explicitly.
 *
 * <h2>Five minutes, from the moment it opens</h2>
 *
 * Not extended by use. A window that renewed on every high-tier action would stay open for as
 * long as someone kept working, which turns a step-up into a mode.
 *
 * <p>In memory, like {@link AdminSessionRegistry} and for the same reason: exactly one backend
 * instance, enforced by the fixed {@code container_name} (SSOT §14.6). A restart closes every
 * window, which fails in the safe direction - an admin re-authenticates, nobody gains anything.
 */
@Component
public class AdminSudoRegistry {

    public static final Duration WINDOW = Duration.ofMinutes(5);

    private final Map<String, Instant> openUntil = new ConcurrentHashMap<>();

    /**
     * Injectable so expiry can be tested by moving time rather than by sleeping for five
     * minutes. AC#2 is about when the window <em>closes</em>, and a test that only checks the
     * expiry timestamp proves the arithmetic, not the behaviour.
     */
    private final Clock clock;

    public AdminSudoRegistry() {
        this(Clock.systemUTC());
    }

    AdminSudoRegistry(Clock clock) {
        this.clock = clock;
    }

    /** @return when the window closes. */
    public Instant open(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("A sudo window needs a session to belong to");
        }
        purgeExpired();
        Instant expiry = clock.instant().plus(WINDOW);
        openUntil.put(sessionId, expiry);
        return expiry;
    }

    public boolean isOpen(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        Instant expiry = openUntil.get(sessionId);
        if (expiry == null) {
            return false;
        }
        if (!expiry.isAfter(clock.instant())) {
            openUntil.remove(sessionId);
            return false;
        }
        return true;
    }

    /** Closed on logout, so a signed-out session cannot leave an armed window behind. */
    public void close(String sessionId) {
        if (sessionId != null) {
            openUntil.remove(sessionId);
        }
    }

    /** Seconds left, or 0 when closed - for the countdown the confirm dialog shows. */
    public long secondsRemaining(String sessionId) {
        if (!isOpen(sessionId)) {
            return 0;
        }
        return Math.max(0, Duration.between(clock.instant(), openUntil.get(sessionId)).toSeconds());
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        openUntil.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    }

    /** Visible for tests and diagnostics. */
    public int size() {
        return openUntil.size();
    }
}
