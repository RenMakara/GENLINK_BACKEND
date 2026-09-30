package kh.edu.acleda.campuslink.shared.exception;

/** Too many attempts. */
public class RateLimitedException extends ApiException {

    public RateLimitedException(String message) {
        super(message, "RATE_LIMITED");
    }
}
