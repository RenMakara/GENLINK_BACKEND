package kh.edu.acleda.campuslink.modules.admin;

/**
 * Admin error codes - a published contract (SSOT §8 C-2).
 *
 * <p>The portal maps {@code errorCode} to a localized message and never parses {@code message}.
 * Renaming a constant here is therefore a breaking API change, not a refactor.
 *
 * <p>Codes are added as the tasks that raise them land, rather than declared up front: an
 * unused constant looks like a contract the backend honours when nothing emits it.
 */
public final class AdminErrorCode {

    private AdminErrorCode() {
    }

    /** 401. Password accepted; a TOTP challenge was issued and must be completed. */
    public static final String ADMIN_MFA_REQUIRED = "ADMIN_MFA_REQUIRED";

    /** 401. Wrong or reused TOTP / recovery code. */
    public static final String ADMIN_MFA_INVALID = "ADMIN_MFA_INVALID";

    /**
     * 403. This admin has not completed TOTP enrolment, so no admin route works except the
     * enrolment ones (SSOT §10.8). The portal sends them to the enrolment screen rather than
     * back to sign-in, which is why it is a 403 and not a 401.
     */
    public static final String ADMIN_MFA_NOT_ENROLLED = "ADMIN_MFA_NOT_ENROLLED";

    /**
     * 403. A high-tier action was attempted without an open sudo window (SSOT §10.7).
     *
     * <p>Not a failure the admin did anything wrong to cause - the portal answers it by
     * prompting for a password or TOTP code and retrying.
     */
    public static final String ADMIN_SUDO_REQUIRED = "ADMIN_SUDO_REQUIRED";

    /**
     * 400. An admin tried to change their own role or deactivate themselves (SSOT §5.1 D5).
     *
     * <p>Not paranoia about self-harm: it is what stops a compromised session from quietly
     * granting itself {@code SUPER_ADMIN}, and it means every privilege increase has a second
     * person's name against it in the audit trail.
     */
    public static final String ADMIN_SELF_MODIFICATION = "ADMIN_SELF_MODIFICATION";

    /**
     * 400. The change would remove the final active {@code SUPER_ADMIN} (SSOT §5.1 D5).
     *
     * <p>The lock-out guard. {@code SUPER_ADMIN} is the only role that can grant
     * {@code rbac:manage}, so with none left nobody can appoint one - recovery needs database
     * access, or a redeploy with the bootstrap property set.
     */
    public static final String ADMIN_LAST_SUPER_ADMIN = "ADMIN_LAST_SUPER_ADMIN";

    /**
     * 400. Enrolment attempted by an admin who has already confirmed TOTP.
     *
     * <p><strong>Not in the SSOT §8 C-2 table</strong> - proposed by ADM-022 and awaiting
     * approval. The frontend has to tell "your code was wrong" apart from "you are already set
     * up, this screen should not be here", and both would otherwise collapse into a generic
     * 400. Re-enrolment is an MFA reset (SSOT §10.8), which needs {@code rbac:manage}.
     */
    public static final String ADMIN_MFA_ALREADY_ENROLLED = "ADMIN_MFA_ALREADY_ENROLLED";

    /** 400. An admin broadcast would exceed the server-side recipient cap. */
    public static final String ADMIN_NOTIFICATION_RECIPIENT_LIMIT = "ADMIN_NOTIFICATION_RECIPIENT_LIMIT";

    /** 400. A selected broadcast recipient is absent, inactive, deleted, or duplicated. */
    public static final String ADMIN_NOTIFICATION_INVALID_RECIPIENTS = "ADMIN_NOTIFICATION_INVALID_RECIPIENTS";

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
