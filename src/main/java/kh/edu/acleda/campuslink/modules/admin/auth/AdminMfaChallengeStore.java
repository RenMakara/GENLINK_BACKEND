package kh.edu.acleda.campuslink.modules.admin.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Per-challenge state for the TOTP step: how many codes have been tried, and whether the challenge has already been spent. */
@Component
public class AdminMfaChallengeStore {

    /** SSOT §10.6: five attempts per challenge, then it is void. */
    public static final int MAX_ATTEMPTS = 5;

    /** How long an entry is kept. */
    private static final Duration RETENTION = Duration.ofMinutes(15);

    /** Sweep only once the map is bigger than this. */
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

    /** Whether the challenge was voided by wrong codes, as opposed to spent by a successful sign-in or never seen. */
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

    /** Retires the challenge after a successful verification. */
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
