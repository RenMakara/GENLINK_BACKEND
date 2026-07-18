package kh.edu.acleda.campuslink.shared.exception;

public class ApiException extends RuntimeException {

    private final String errorCode;

    public ApiException(String message) {
        this(message, "API_ERROR");
    }

    public ApiException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
