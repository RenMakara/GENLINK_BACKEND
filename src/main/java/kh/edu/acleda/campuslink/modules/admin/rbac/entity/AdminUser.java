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

/**
 * A staff member: an existing {@link User} plus an admin profile.
 *
 * <p>Admins are not a separate identity. That reuses BCrypt, JWT issuance, refresh tokens,
 * session revocation, {@code token_version}, rate limiting and {@code security_audit_logs} -
 * all built, tested, and already carrying bug fixes. A parallel identity system would need
 * every one of those rebuilt, and would need them to be correct.
 *
 * <p><strong>Hard delete is effectively impossible once this admin has acted:</strong>
 * {@code admin_audit_logs} references it with no cascade, so Postgres rejects the delete. That
 * is deliberate - you must not be able to erase yourself from the audit trail by removing your
 * profile. Revocation is {@link #active} = false plus {@link #deletedAt}.
 */
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

    /**
     * TOTP shared secret. <strong>A credential.</strong> Encrypted before it reaches this
     * field, with a key from environment configuration - never plaintext, never returned in an
     * API response after enrolment, never logged. Same discipline as hashing refresh tokens.
     */
    @Column(name = "totp_secret", columnDefinition = "TEXT")
    private String totpSecret;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled;

    /** Null until enrolment is confirmed. Admin endpoints stay closed until it is set. */
    @Column(name = "totp_confirmed_at")
    private Instant totpConfirmedAt;

    /**
     * Ten single-use recovery codes as {@code [{"hash": "...", "usedAt": null}]}, BCrypt-hashed.
     *
     * <p>JSONB rather than one concatenated string because the codes are individually
     * consumable - a single hash column could not record which of the ten had been spent, so
     * using one would have to invalidate all ten or none.
     */
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
