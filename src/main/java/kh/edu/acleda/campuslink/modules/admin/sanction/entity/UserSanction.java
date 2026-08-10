package kh.edu.acleda.campuslink.modules.admin.sanction.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionSeverity;
import kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionType;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One enforcement action against a user: a warning, a suspension or a ban.
 *
 * <p>This table is the <em>history</em>; the user's current state stays on
 * {@code users.account_status}. The split means the hot path ("is this user allowed in?")
 * reads one column on {@code users}, while "what have we done about this user?" is one ordered
 * query here.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_sanctions")
@AttributeOverride(name = "id", column = @Column(name = "user_sanction_id", nullable = false, updatable = false))
public class UserSanction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Who issued it. Never null - an unattributed sanction is not auditable. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private AdminUser adminUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private SanctionType type;

    /** Meaningful on warnings; null on bans, which have a reason rather than a severity. */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20)
    private SanctionSeverity severity;

    /** Internal justification. Shown to staff, never to the user. */
    @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
    private String reason;

    /** Shown TO the user, so it must exist in Khmer as well as English. Null for a ban. */
    @Column(name = "message_en", columnDefinition = "TEXT")
    private String messageEn;

    @Column(name = "message_km", columnDefinition = "TEXT")
    private String messageKm;

    /**
     * The {@code account_status} this sanction replaced, restored when it is lifted.
     *
     * <p><strong>This field is why lifting is correct.</strong> Setting {@code ACTIVE}
     * unconditionally on unban would promote a {@code PENDING} user - registered but never
     * email-verified - into a verified account they never earned. Captured at creation,
     * restored on lift and on automatic expiry.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 30)
    private AccountStatus previousStatus;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    /** Null means indefinite: a ban, or a warning, which never expires on its own. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** Set when the user acknowledges a warning in the mobile app. */
    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "lifted_at")
    private Instant liftedAt;

    /** Null when lifted automatically by the expiry job rather than by a person. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lifted_by")
    private AdminUser liftedBy;

    @Column(name = "lift_reason", columnDefinition = "TEXT")
    private String liftReason;

    /** What triggered it, e.g. {@code POST} plus the post's id. Both optional. */
    @Column(name = "related_type", length = 40)
    private String relatedType;

    @Column(name = "related_id")
    private UUID relatedId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Still in force: not lifted, and not past its expiry. */
    public boolean isInForce(Instant now) {
        return liftedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }
}
