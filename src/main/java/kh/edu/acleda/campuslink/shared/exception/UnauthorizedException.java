package kh.edu.acleda.campuslink.shared.exception;

public class UnauthorizedException extends ApiException {

    /** The generic 401. Error code {@code UNAUTHORIZED}. */
    public UnauthorizedException(String message) {
        this(message, "UNAUTHORIZED");
    }

    /**
     * A 401 that names a more specific cause, such as {@code ADMIN_MFA_INVALID}.
     *
     * <p>The default above is the code this exception has always produced - it used to be
     * hard-coded in the handler rather than carried here, so moving it into the constructor
     * changes nothing for the existing callers while letting new ones be precise.
     */
    public UnauthorizedException(String message, String errorCode) {
        super(message, errorCode);
    }
}
