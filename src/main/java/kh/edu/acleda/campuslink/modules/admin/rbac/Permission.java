package kh.edu.acleda.campuslink.modules.admin.rbac;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** The admin permission catalogue. */
public enum Permission {

    // -- Dashboard and analytics ---------------------------------------------------------
    DASHBOARD_VIEW("dashboard:view", "View the admin dashboard"),
    ANALYTICS_VIEW("analytics:view", "View analytics dashboards"),
    ANALYTICS_EXPORT("analytics:export", "Export analytics series as CSV"),

    // -- User records --------------------------------------------------------------------
    USER_READ("user:read", "View user records"),
    USER_UPDATE("user:update", "Correct user profile fields"),
    USER_EXPORT("user:export", "Export the user list as CSV"),
    /** Separate from {@link #USER_EXPORT}: the easiest way to exfiltrate personal data. */
    USER_EXPORT_PII("user:export:pii", "Include phone and date of birth in exports"),

    // -- User enforcement ----------------------------------------------------------------
    USER_BAN("user:ban", "Ban a user permanently"),
    USER_UNBAN("user:unban", "Lift a ban"),
    USER_SUSPEND("user:suspend", "Suspend a user for a fixed period"),
    USER_WARN("user:warn", "Issue a formal warning"),
    USER_NOTE_WRITE("user:note:write", "Write internal staff notes on a user"),

    // -- Account recovery and lifecycle --------------------------------------------------
    USER_VERIFY_EMAIL("user:verify_email", "Manually mark an email verified"),
    USER_RESET_PASSWORD("user:reset_password", "Trigger a password-reset email"),
    USER_FORCE_LOGOUT("user:force_logout", "Revoke a user's sessions"),
    USER_DELETE("user:delete", "Soft-delete and restore a user account"),

    // -- Gamification --------------------------------------------------------------------
    XP_GRANT("xp:grant", "Grant XP to a user"),
    XP_REVOKE("xp:revoke", "Remove XP from a user"),
    BADGE_ASSIGN("badge:assign", "Assign badges to users"),
    GAMIFICATION_MANAGE("gamification:manage", "Manage badges, challenges and leaderboards"),

    // -- Content moderation --------------------------------------------------------------
    COMMUNITY_MODERATE("community:moderate", "Moderate posts, comments and communities"),
    FORUM_MODERATE("forum:moderate", "Moderate forum discussions"),
    STORY_MODERATE("story:moderate", "Moderate stories"),
    EVENT_READ("event:read", "View events"),
    EVENT_MANAGE("event:manage", "Publish, cancel and edit events"),
    OPPORTUNITY_MANAGE("opportunity:manage", "Manage the opportunity hub"),
    MEDIA_READ("media:read", "Browse the media library"),
    MEDIA_DELETE("media:delete", "Delete media files"),

    // -- Notifications -------------------------------------------------------------------
    NOTIFICATION_SEND("notification:send", "Send notifications and broadcasts"),
    NOTIFICATION_TEMPLATE_MANAGE("notification:template:manage", "Manage notification templates"),

    // -- Reports -------------------------------------------------------------------------
    REPORT_READ("report:read", "View abuse and content report queues"),
    REPORT_RESOLVE("report:resolve", "Resolve or dismiss reports"),

    // -- Finance -------------------------------------------------------------------------
    FINANCE_READ("finance:read", "View finance figures"),
    FINANCE_LEDGER_READ("finance:ledger:read", "Inspect individual ledger transactions"),

    // -- Administration ------------------------------------------------------------------
    RBAC_READ("rbac:read", "View roles, permissions and admin users"),
    RBAC_MANAGE("rbac:manage", "Change roles, permissions and admin users"),
    AUDIT_READ("audit:read", "Read the admin audit log"),
    LOCALIZATION_MANAGE("localization:manage", "Edit bilingual content"),
    SETTINGS_READ("settings:read", "View system settings"),
    SETTINGS_MANAGE("settings:manage", "Change system settings");

    /** Prefix that keeps these clear of {@code ROLE_} authorities. */
    public static final String AUTHORITY_PREFIX = "PERM_";

    private static final Map<String, Permission> BY_CODE;

    static {
        Map<String, Permission> byCode = new LinkedHashMap<>();
        for (Permission permission : values()) {
            Permission clash = byCode.put(permission.code, permission);
            if (clash != null) {
                // Two constants sharing a code would silently grant one when the other was meant.
                throw new IllegalStateException(
                    "Duplicate permission code '" + permission.code + "' on " + clash + " and " + permission);
            }
        }
        BY_CODE = Collections.unmodifiableMap(byCode);
    }

    private final String code;
    private final String description;

    Permission(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /** The stored form, e.g. {@code user:ban}. This is what {@code role_permissions} holds. */
    public String getCode() {
        return code;
    }

    /** Human-readable purpose, shown in the role editor's permission matrix. */
    public String getDescription() {
        return description;
    }

    /** First segment of the code, used to group the matrix by resource in the role editor. */
    public String getResource() {
        return code.substring(0, code.indexOf(':'));
    }

    /** Everything after the resource, e.g. {@code export:pii}. */
    public String getAction() {
        return code.substring(code.indexOf(':') + 1);
    }

    /** Mirrors {@code Role.asAuthority()}, which is the pattern already used for roles. */
    public SimpleGrantedAuthority asAuthority() {
        return new SimpleGrantedAuthority(AUTHORITY_PREFIX + code);
    }

    /** Resolves a stored code. */
    public static Optional<Permission> fromCode(String code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }

    /** All codes, in declaration order. Backs {@code GET /api/v1/admin/permissions}. */
    public static List<String> allCodes() {
        return Arrays.stream(values()).map(Permission::getCode).toList();
    }
}
