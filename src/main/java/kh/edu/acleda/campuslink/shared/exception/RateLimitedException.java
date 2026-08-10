package kh.edu.acleda.campuslink.shared.exception;

/**
 * Too many attempts. Maps to {@code 429} with error code {@code RATE_LIMITED} (SSOT §8 C-2).
 *
 * <p>Distinct from the {@code AUTH_TOO_MANY_ATTEMPTS} that {@code AuthRateLimitService} raises
 * on the mobile paths, which surfaces as a {@code 400}. That is long-standing behaviour with
 * clients in the field, so it is left alone; the admin portal is new and is built to the
 * contract the SSOT publishes. The two should converge, but not as a side effect of this task.
 */
public class RateLimitedException extends ApiException {

    public RateLimitedException(String message) {
        super(message, "RATE_LIMITED");
    }
}
