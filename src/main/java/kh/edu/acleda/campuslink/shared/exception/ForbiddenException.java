package kh.edu.acleda.campuslink.shared.exception;

/**
 * Authenticated, but not allowed. Maps to {@code 403}.
 *
 * <p>Distinct from {@link UnauthorizedException} (401) on purpose: the admin portal reacts
 * differently to the two. A 401 sends the user back to the sign-in screen; a 403 means they
 * are signed in and the screen they reached is simply not theirs, so bouncing them to login
 * would be a confusing loop that fixes nothing.
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message, String errorCode) {
        super(message, errorCode);
    }
}
