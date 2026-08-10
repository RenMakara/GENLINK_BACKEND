package kh.edu.acleda.campuslink.modules.admin.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Per-challenge state for the TOTP step: how many codes have been tried, and whether the
 * challenge has already been spent.
 *
 * <h2>Why this exists at all</h2>
 *
 * The challenge is a JWT, which is stateless by construction - it stays valid for its full five
 * minutes no matter what anyone does with it. Two things in SSOT §10.6 need it to be otherwise:
 * a cap of five attempts, and (see below) single use. Both need somewhere to remember what has
 * happened, keyed on the {@code jti} the token carries.
 *
 * <h2>Single use is a decision, not a spec</h2>
 *
 * §10.6 caps attempts but does not say whether a <em>successful</em> verification retires the
 * challenge. It does here. Otherwise a challenge captured from a proxy log or a browser history
 * entry stays usable for the rest of its window and mints a second admin session - one that
 * needed no password and no code, because both were already spent by the legitimate login.
 *
 * <h2>In memory, like the rate limiter</h2>
 *
 * A {@code ConcurrentHashMap}, correct only because the backend runs exactly one instance
 * (SSOT §14.6, enforced by {@code container_name} in docker-compose.yml). On two replicas an
 * attacker would simply spread five guesses across each. It is listed in that file's
 * load-bearing comment for the same reason the other three are.
 *
 * <p>The residual weakness is the same one §10.6 already accepts for rate limiting: a restart
 * clears the map, so an attacker mid-attempt gets a fresh five. Bounded by how often the
 * service restarts, and not on its own a reason to introduce Redis.
 */
@Component
public class AdminMfaChallengeStore {

    /** SSOT §10.6: five attempts per challenge, then it is void. */
    public static final int MAX_ATTEMPTS = 5;

    /**
     * How long an entry is kept. Comfortably longer than the 5-minute challenge lifetime, so a
     * challenge can never outlive its own attempt counter - which would silently reset the cap.
     */
    private static final Duration RETENTION = Duration.ofMinutes(15);

    /**
     * Sweep only once the map is bigger than this. Entries are created solely for challenges
     * that already survived a password check, so growth is bounded by real sign-ins; this is
     * hygiene against a long-running instance, not a defence against flooding.
     */
    private static final int SWEEP_THRESHOLD = 256;

    private final Map<String, State> states = new ConcurrentHashMap<>();

    private record State(int attempts, boolean spent, Instant firstSeen) {

        boolean usable() {
            return !spent && attempts < MAX_ATTEMPTS;
        }
    }

    /** False once the challenge has been spent or has run out of attempts. */
    public boolean isUsable(String challengeId) {
        State state = states.get(challengeId);
        return state == null || state.usable();
    }

    /** @return attempts left, for the "2 tries remaining" hint the portal shows. */
    public int remainingAttempts(String challengeId) {
        State state = states.get(challengeId);
        if (state == null) {
            return MAX_ATTEMPTS;
        }
        return state.spent() ? 0 : Math.max(0, MAX_ATTEMPTS - state.attempts());
    }

    /**
     * Whether the challenge was voided by wrong codes, as opposed to spent by a successful
     * sign-in or never seen.
     *
     * <p>The two look identical to {@link #isUsable} but are different answers: running out of
     * attempts is rate limiting and earns a {@code 429}, while a spent challenge is a replay and
     * earns a {@code 401}. Collapsing them would tell an admin who mistyped five times that
     * their session expired, and send them round the loop again instead of telling them to wait.
     *
     * <p>Safe to disclose - reaching this at all requires a challenge token, which requires the
     * correct password.
     */
    public boolean isVoidedByAttempts(String challengeId) {
        State state = states.get(challengeId);
        return state != null && !state.spent() && state.attempts() >= MAX_ATTEMPTS;
    }

    /** Counts a wrong code. The fifth one voids the challenge. */
    public void recordFailure(String challengeId) {
        sweepIfCrowded();
        states.compute(challengeId, (ignored, state) -> state == null
            ? new State(1, false, Instant.now())
            : new State(state.attempts() + 1, state.spent(), state.firstSeen()));
    }

    /**
     * Retires the challenge after a successful verification.
     *
     * <p>Not a removal: removing the entry would make the next presentation of the same token
     * look like a challenge nobody had touched, which is exactly the replay this prevents.
     */
    public void spend(String challengeId) {
        sweepIfCrowded();
        states.compute(challengeId, (ignored, state) -> state == null
            ? new State(0, true, Instant.now())
            : new State(state.attempts(), true, state.firstSeen()));
    }

    private void sweepIfCrowded() {
        if (states.size() <= SWEEP_THRESHOLD) {
            return;
        }
        Instant cutoff = Instant.now().minus(RETENTION);
        states.values().removeIf(state -> state.firstSeen().isBefore(cutoff));
    }
}
