package kh.edu.acleda.campuslink.modules.admin.rbac.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A staff member: an existing {@link User} plus an admin profile. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "admin_users")
@AttributeOverride(name = "id", column = @Column(name = "admin_user_id", nullable = false, updatable = false))
public class AdminUser extends BaseEntity {

    /** One admin profile per user; the unique constraint is enforced in the schema. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_role_id", nullable = false)
    private AdminRole adminRole;

    /** Cleared to revoke staff access while keeping the person's user account intact. */
    @Column(name = "is_active", nullable = false)
    private boolean active;

    /** TOTP shared secret. */
    @Column(name = "totp_secret", columnDefinition = "TEXT")
    private String totpSecret;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled;

    /** Null until enrolment is confirmed. Admin endpoints stay closed until it is set. */
    @Column(name = "totp_confirmed_at")
    private Instant totpConfirmedAt;

    /** Ten single-use recovery codes as {@code [{"hash": "...", "usedAt": null}]}, BCrypt-hashed. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recovery_codes")
    private String recoveryCodes;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** IPv6-capable width, matching {@code refresh_tokens.ip_address}. */
    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    /** Null for the bootstrap admin, who by definition had nobody to invite them. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by")
    private AdminUser invitedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** True once enrolment is confirmed; until then only the enrolment routes are reachable. */
    public boolean isEnrolled() {
        return totpConfirmedAt != null;
    }

    /** Usable staff account: active, not soft-deleted. Enrolment is checked separately. */
    public boolean isUsable() {
        return active && deletedAt == null;
    }
}
