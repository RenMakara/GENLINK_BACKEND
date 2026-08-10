package kh.edu.acleda.campuslink.shared.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final String errorCode;
    private final Instant timestamp;

    private ApiResponse(boolean success, String message, T data, String errorCode) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.errorCode = errorCode;
        this.timestamp = Instant.now();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static <T> ApiResponse<T> failure(String message) {
        return failure(message, "API_ERROR");
    }

    public static <T> ApiResponse<T> failure(String message, String errorCode) {
        return new ApiResponse<>(false, message, null, errorCode);
    }

    /**
     * A failure that still carries a payload.
     *
     * <p>Rare, and it should stay rare: almost every failure has nothing useful to return, and
     * the two-argument form above is the one to reach for. The case this exists for is the
     * admin MFA challenge (SSOT §8.1) - the password step answers {@code 401} with
     * {@code ADMIN_MFA_REQUIRED}, because the caller is *not* authenticated yet, while still
     * handing back the challenge token needed for the second step.
     *
     * <p>Modelling that as a success would be worse: the frontend's error handling keys on
     * {@code success == false}, and a {@code 200} here would make "logged in" and "half logged
     * in" indistinguishable to every generic client.
     */
    public static <T> ApiResponse<T> failure(String message, String errorCode, T data) {
        return new ApiResponse<>(false, message, data, errorCode);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
