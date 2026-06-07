package kh.edu.acleda.campuslink.modules.auth.security;

import kh.edu.acleda.campuslink.modules.auth.AuthErrorCode;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthRateLimitService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long WINDOW_MINUTES = 15;
    private static final long BLOCK_MINUTES = 15;

    private final Map<String, LoginAttemptState> loginAttempts = new ConcurrentHashMap<>();

    public void checkLoginAllowed(String email, String clientIp) {
        LoginAttemptState state = loginAttempts.get(key(email, clientIp));
        if (state != null && state.blockedUntil != null && state.blockedUntil.isAfter(Instant.now())) {
            throw new ApiException("Too many login attempts. Please try again later.", AuthErrorCode.AUTH_TOO_MANY_ATTEMPTS);
        }
    }

    public void recordLoginFailure(String email, String clientIp) {
        String key = key(email, clientIp);
        Instant now = Instant.now();
        loginAttempts.compute(key, (ignored, state) -> {
            if (state == null || state.windowStartedAt.plus(WINDOW_MINUTES, ChronoUnit.MINUTES).isBefore(now)) {
                return new LoginAttemptState(now, 1, null);
            }
            int failedAttempts = state.failedAttempts + 1;
            Instant blockedUntil = failedAttempts >= MAX_FAILED_ATTEMPTS
                ? now.plus(BLOCK_MINUTES, ChronoUnit.MINUTES)
                : state.blockedUntil;
            return new LoginAttemptState(state.windowStartedAt, failedAttempts, blockedUntil);
        });
        // TODO: Replace in-memory rate limiter with Redis for distributed deployment.
    }

    public void clearLoginFailures(String email, String clientIp) {
        loginAttempts.remove(key(email, clientIp));
    }

    private String key(String email, String clientIp) {
        String normalizedEmail = email == null ? "unknown" : email.trim().toLowerCase();
        String normalizedIp = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
        return normalizedEmail + "|" + normalizedIp;
    }

    private record LoginAttemptState(Instant windowStartedAt, int failedAttempts, Instant blockedUntil) {
    }
}
