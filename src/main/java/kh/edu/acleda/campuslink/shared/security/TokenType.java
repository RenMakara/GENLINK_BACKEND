package kh.edu.acleda.campuslink.shared.security;

/** Which API an access token is for. */
public enum TokenType {

    /** The Flutter app. Default for tokens issued before this claim existed. */
    MOBILE,

    /** The admin portal. Shorter-lived, and required by every admin route. */
    ADMIN,

    /** The 5-minute token handed out after the password step of admin login, before TOTP. */
    ADMIN_MFA_CHALLENGE;

    /** JWT claim name. Short, because it is on every request. */
    public static final String CLAIM = "typ";

    /** Reads the claim off a parsed token. */
    public static TokenType fromClaim(Object claim) {
        if (claim == null) {
            return MOBILE;
        }
        String value = claim.toString();
        for (TokenType type : values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        return MOBILE;
    }
}
