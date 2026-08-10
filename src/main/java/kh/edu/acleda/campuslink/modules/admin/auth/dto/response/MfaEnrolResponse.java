package kh.edu.acleda.campuslink.modules.admin.auth.dto.response;

/**
 * The one and only time a TOTP secret leaves the server.
 *
 * @param secret Base32 shared secret, for admins whose authenticator cannot scan a QR code.
 *     Returned here because enrolment is impossible without it, and never again afterwards -
 *     {@code /admin/auth/me} and every other endpoint omit it (SSOT §9.2).
 * @param otpauthUri the {@code otpauth://totp/...} URI the portal renders as a QR code. Carries
 *     the same secret; it is a convenience, not a second credential.
 */
public record MfaEnrolResponse(
    String secret,
    String otpauthUri
) {
}
