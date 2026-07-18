package kh.edu.acleda.campuslink.modules.auth;

public final class AuthErrorCode {

    private AuthErrorCode() {
    }

    public static final String AUTH_INVALID_CREDENTIALS = "AUTH_INVALID_CREDENTIALS";
    public static final String AUTH_ACCOUNT_PENDING = "AUTH_ACCOUNT_PENDING";
    public static final String AUTH_ACCOUNT_BANNED = "AUTH_ACCOUNT_BANNED";
    public static final String AUTH_ACCOUNT_DELETED = "AUTH_ACCOUNT_DELETED";
    public static final String AUTH_EMAIL_NOT_VERIFIED = "AUTH_EMAIL_NOT_VERIFIED";
    public static final String AUTH_ACCOUNT_NOT_ALLOWED = "AUTH_ACCOUNT_NOT_ALLOWED";
    public static final String AUTH_TOO_MANY_ATTEMPTS = "AUTH_TOO_MANY_ATTEMPTS";
    public static final String AUTH_WEAK_PASSWORD = "AUTH_WEAK_PASSWORD";
    public static final String OTP_INVALID = "OTP_INVALID";
    public static final String OTP_EXPIRED = "OTP_EXPIRED";
    public static final String OTP_ALREADY_USED = "OTP_ALREADY_USED";
    public static final String OTP_TOO_MANY_ATTEMPTS = "OTP_TOO_MANY_ATTEMPTS";
    public static final String OTP_RESEND_TOO_SOON = "OTP_RESEND_TOO_SOON";
    public static final String OTP_RESEND_LIMIT_EXCEEDED = "OTP_RESEND_LIMIT_EXCEEDED";
    public static final String TOKEN_INVALID = "TOKEN_INVALID";
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final String TOKEN_REVOKED = "TOKEN_REVOKED";
    public static final String TOKEN_REUSE_DETECTED = "TOKEN_REUSE_DETECTED";
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
}
