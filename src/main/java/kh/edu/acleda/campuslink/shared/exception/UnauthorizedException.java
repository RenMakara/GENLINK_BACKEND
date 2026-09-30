package kh.edu.acleda.campuslink.shared.exception;

public class UnauthorizedException extends ApiException {

    /** The generic 401. Error code {@code UNAUTHORIZED}. */
    public UnauthorizedException(String message) {
        this(message, "UNAUTHORIZED");
    }

    /** A 401 that names a more specific cause, such as {@code ADMIN_MFA_INVALID}. */
    public UnauthorizedException(String message, String errorCode) {
        super(message, errorCode);
    }
}
