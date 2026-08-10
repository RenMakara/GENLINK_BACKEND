package kh.edu.acleda.campuslink.modules.auth.security;

import kh.edu.acleda.campuslink.modules.auth.AuthErrorCode;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Throttles credential guessing, keyed per action so that, for example, failed
 * change-password attempts cannot lock the account out of login and vice versa.
 */
@Service
public class AuthRateLimitService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long WINDOW_MINUTES = 15;
    private static final long BLOCK_MINUTES = 15;

    private static final String LOGIN = "LOGIN";
    private static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
    private static final String ACCOUNT_DELETE = "ACCOUNT_DELETE";
    /**
     * Admin login counts separately from mobile login (SSOT §10.6).
     *
     * <p>Sharing the bucket would let anyone lock an administrator out of the portal by failing
     * five mobile logins against their email - a denial-of-service against the people who
     * respond to incidents, from an unauthenticated endpoint.
     */
    private static final String ADMIN_LOGIN = "ADMIN_LOGIN";

    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();

    /**
     * Injectable so the 15-minute window and the 15-minute block can be tested by moving time
     * rather than by sleeping for half an hour. Production uses the system clock.
     */
    private final Clock clock;

    public AuthRateLimitService() {
        this(Clock.systemUTC());
    }

    AuthRateLimitService(Clock clock) {
        this.clock = clock;
    }

    public void checkLoginAllowed(String email, String clientIp) {
        checkAllowed(LOGIN, email, clientIp, "Too many login attempts. Please try again later.");
    }

    public void recordLoginFailure(String email, String clientIp) {
        recordFailure(LOGIN, email, clientIp);
    }

    public void clearLoginFailures(String email, String clientIp) {
        clearFailures(LOGIN, email, clientIp);
    }

    /**
     * Reports the block instead of throwing, so the admin endpoint can raise its own 429 and
     * write an audit row first. The mobile paths keep the throwing form above.
     */
    public boolean isAdminLoginBlocked(String email, String clientIp) {
        AttemptState state = attempts.get(key(ADMIN_LOGIN, email, clientIp));
        return state != null && state.blockedUntil != null && state.blockedUntil.isAfter(clock.instant());
    }

    public void recordAdminLoginFailure(String email, String clientIp) {
        recordFailure(ADMIN_LOGIN, email, clientIp);
    }

    public void clearAdminLoginFailures(String email, String clientIp) {
        clearFailures(ADMIN_LOGIN, email, clientIp);
    }

    public void checkPasswordChangeAllowed(String email, String clientIp) {
        checkAllowed(PASSWORD_CHANGE, email, clientIp, "Too many password change attempts. Please try again later.");
    }

    public void recordPasswordChangeFailure(String email, String clientIp) {
        recordFailure(PASSWORD_CHANGE, email, clientIp);
    }

    public void clearPasswordChangeFailures(String email, String clientIp) {
        clearFailures(PASSWORD_CHANGE, email, clientIp);
    }

    public void checkAccountDeleteAllowed(String email, String clientIp) {
        checkAllowed(ACCOUNT_DELETE, email, clientIp, "Too many account deletion attempts. Please try again later.");
    }

    public void recordAccountDeleteFailure(String email, String clientIp) {
        recordFailure(ACCOUNT_DELETE, email, clientIp);
    }

    public void clearAccountDeleteFailures(String email, String clientIp) {
        clearFailures(ACCOUNT_DELETE, email, clientIp);
    }

    private void checkAllowed(String action, String email, String clientIp, String message) {
        AttemptState state = attempts.get(key(action, email, clientIp));
        if (state != null && state.blockedUntil != null && state.blockedUntil.isAfter(clock.instant())) {
            throw new ApiException(message, AuthErrorCode.AUTH_TOO_MANY_ATTEMPTS);
        }
    }

    private void recordFailure(String action, String email, String clientIp) {
        String key = key(action, email, clientIp);
        Instant now = clock.instant();
        attempts.compute(key, (ignored, state) -> {
            if (state == null || state.windowStartedAt.plus(WINDOW_MINUTES, ChronoUnit.MINUTES).isBefore(now)) {
                return new AttemptState(now, 1, null);
            }
            int failedAttempts = state.failedAttempts + 1;
            Instant blockedUntil = failedAttempts >= MAX_FAILED_ATTEMPTS
                ? now.plus(BLOCK_MINUTES, ChronoUnit.MINUTES)
                : state.blockedUntil;
            return new AttemptState(state.windowStartedAt, failedAttempts, blockedUntil);
        });
        // TODO: Replace in-memory rate limiter with Redis for distributed deployment.
    }

    private void clearFailures(String action, String email, String clientIp) {
        attempts.remove(key(action, email, clientIp));
    }

    private String key(String action, String email, String clientIp) {
        String normalizedEmail = email == null ? "unknown" : email.trim().toLowerCase();
        String normalizedIp = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
        return action + "|" + normalizedEmail + "|" + normalizedIp;
    }

    private record AttemptState(Instant windowStartedAt, int failedAttempts, Instant blockedUntil) {
    }
}
