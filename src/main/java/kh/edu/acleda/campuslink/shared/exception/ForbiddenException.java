package kh.edu.acleda.campuslink.shared.exception;

/** Authenticated, but not allowed. */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message, String errorCode) {
        super(message, errorCode);
    }
}
