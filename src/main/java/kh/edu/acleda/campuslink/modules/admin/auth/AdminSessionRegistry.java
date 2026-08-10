package kh.edu.acleda.campuslink.modules.admin.auth;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Admin access tokens that have been signed out before they expired.
 *
 * <h2>Why this exists at all</h2>
 *
 * An access token is a signed JWT: the server does not store it, so it stays valid until it
 * expires no matter what anyone does. That is normally the point. It is wrong for an admin
 * portal, where "log out" on a shared or unattended machine has to mean the token stops working
 * now, not in up to ten minutes.
 *
 * <h2>Why not just bump token_version</h2>
 *
 * {@code token_version} already invalidates tokens, and it is one line - but it invalidates
 * <em>every</em> token that person holds, so signing out of a browser tab would also sign them
 * out of the mobile app on their phone. SSOT §8.1 says logout revokes "this session".
 *
 * <p>So each admin access token carries a session id as {@code jti}, and logout records that
 * one id here.
 *
 * <h2>In memory, deliberately</h2>
 *
 * A {@code ConcurrentHashMap}, correct only because the backend runs exactly one instance -
 * structurally enforced by the fixed {@code container_name} in {@code docker-compose.yml}
 * (SSOT §14.6). Same reasoning, and the same exit path, as {@code AuthRateLimitService} and
 * {@code AdminMfaChallengeStore}: a second replica needs Redis for all three.
 *
 * <p><strong>Restart clears it.</strong> A revoked session would come back to life - except
 * that it cannot, because access tokens live ten minutes and a restart takes longer than that
 * to be worth timing. The refresh token is revoked in the database and is unaffected either
 * way, so a restart cannot resurrect a session, only at most the tail of one access token.
 */
@Component
public class AdminSessionRegistry {

    /** Session id to the moment its access token expires, after which the entry is noise. */
    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();

    /**
     * @param expiresAt when the revoked token would have expired anyway. Entries are only kept
     *     until then - without it this map grows for the life of the process, one entry per
     *     logout, forever.
     */
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
        // A token past its own expiry is already refused by signature validation, so the entry
        // no longer protects anything and is dropped on the way past.
        if (expiry.isBefore(Instant.now())) {
            revoked.remove(sessionId);
            return false;
        }
        return true;
    }

    /**
     * Swept on write rather than on a schedule.
     *
     * <p>Logouts are rare and the map is small, so a scan costs nothing; a {@code @Scheduled}
     * task would be a second moving part to reason about for no benefit at this size.
     */
    private void purgeExpired() {
        Instant now = Instant.now();
        revoked.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }

    /** Visible for tests and diagnostics. */
    public int size() {
        return revoked.size();
    }
}
