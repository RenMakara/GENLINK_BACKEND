package kh.edu.acleda.campuslink.modules.admin.auth.dto.response;

/** The one and only time a TOTP secret leaves the server. */
public record MfaEnrolResponse(
    String secret,
    String otpauthUri
) {
}
