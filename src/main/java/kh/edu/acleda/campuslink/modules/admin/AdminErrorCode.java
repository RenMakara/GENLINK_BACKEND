package kh.edu.acleda.campuslink.modules.admin;

/** Admin error codes - a published contract (SSOT §8 C-2). */
public final class AdminErrorCode {

    private AdminErrorCode() {
    }

    /** 401. Password accepted; a TOTP challenge was issued and must be completed. */
    public static final String ADMIN_MFA_REQUIRED = "ADMIN_MFA_REQUIRED";

    /** 401. Wrong or reused TOTP / recovery code. */
    public static final String ADMIN_MFA_INVALID = "ADMIN_MFA_INVALID";

    /** Admin has not completed TOTP enrollment. */
    public static final String ADMIN_MFA_NOT_ENROLLED = "ADMIN_MFA_NOT_ENROLLED";

    /** High-tier action requires a fresh sudo window. */
    public static final String ADMIN_SUDO_REQUIRED = "ADMIN_SUDO_REQUIRED";

    /** Admin cannot change their own role or status. */
    public static final String ADMIN_SELF_MODIFICATION = "ADMIN_SELF_MODIFICATION";

    /** Change cannot remove the final active SUPER_ADMIN. */
    public static final String ADMIN_LAST_SUPER_ADMIN = "ADMIN_LAST_SUPER_ADMIN";

    /** TOTP is already enrolled. */
    public static final String ADMIN_MFA_ALREADY_ENROLLED = "ADMIN_MFA_ALREADY_ENROLLED";

    /** 400. An admin broadcast would exceed the server-side recipient cap. */
    public static final String ADMIN_NOTIFICATION_RECIPIENT_LIMIT = "ADMIN_NOTIFICATION_RECIPIENT_LIMIT";

    /** 400. A selected broadcast recipient is absent, inactive, deleted, or duplicated. */
    public static final String ADMIN_NOTIFICATION_INVALID_RECIPIENTS = "ADMIN_NOTIFICATION_INVALID_RECIPIENTS";

    /** 400. SUPER_ADMIN permissions are implicit in code and cannot be replaced from data. */
    public static final String ADMIN_SUPER_ADMIN_PERMISSIONS_IMMUTABLE =
        "ADMIN_SUPER_ADMIN_PERMISSIONS_IMMUTABLE";

    /** 400. A broadcast has no eligible recipients. */
    public static final String ADMIN_NOTIFICATION_NO_RECIPIENTS = "ADMIN_NOTIFICATION_NO_RECIPIENTS";

    /** 400. A bulk request contains more than the server-side 500-user cap. */
    public static final String BULK_LIMIT_EXCEEDED = "BULK_LIMIT_EXCEEDED";

    /** 400. A CSV export exceeds the server-side 50,000-row cap. */
    public static final String EXPORT_TOO_LARGE = "EXPORT_TOO_LARGE";

    /** 400. The template key is not an admin-managed key or its localized content is invalid. */
    public static final String ADMIN_NOTIFICATION_TEMPLATE_INVALID = "ADMIN_NOTIFICATION_TEMPLATE_INVALID";

    /** 404. The requested admin-managed template does not exist. */
    public static final String ADMIN_NOTIFICATION_TEMPLATE_NOT_FOUND = "ADMIN_NOTIFICATION_TEMPLATE_NOT_FOUND";

    /** 400. An admin-managed template key is already in use. */
    public static final String ADMIN_NOTIFICATION_TEMPLATE_DUPLICATE = "ADMIN_NOTIFICATION_TEMPLATE_DUPLICATE";

    /** 400. The target already has an open ban. */
    public static final String USER_ALREADY_BANNED = "USER_ALREADY_BANNED";

    /** 400. The target has no open ban to lift. */
    public static final String USER_NOT_BANNED = "USER_NOT_BANNED";

    /** 400. The target is beyond the reversible account-deletion stage. */
    public static final String USER_ANONYMISED = "USER_ANONYMISED";
}
