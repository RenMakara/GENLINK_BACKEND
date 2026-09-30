package kh.edu.acleda.campuslink.modules.admin.auth;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Admin access tokens that have been signed out before they expired. */
@Component
public class AdminSessionRegistry {

    /** Session id to the moment its access token expires, after which the entry is noise. */
    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();

    /** @param expiresAt when the revoked token would have expired anyway. Entries are only kept */
    public void revoke(String sessionId, Instant expiresAt) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        purgeExpired();
        revoked.put(sessionId, expiresAt);
    }

    /** Null is not revoked: tokens issued before session ids existed carry no {@code jti}. */
    public boolean isRevoked(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        Instant expiry = revoked.get(sessionId);
        if (expiry == null) {
            return false;
        }
        // A token past its own expiry is already refused by signature validation.
        if (expiry.isBefore(Instant.now())) {
            revoked.remove(sessionId);
            return false;
        }
        return true;
    }

    /** Swept on write rather than on a schedule. */
    private void purgeExpired() {
        Instant now = Instant.now();
        revoked.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }

    /** Visible for tests and diagnostics. */
    public int size() {
        return revoked.size();
    }
}
