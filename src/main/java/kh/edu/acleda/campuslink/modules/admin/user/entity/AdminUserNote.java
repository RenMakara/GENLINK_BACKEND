package kh.edu.acleda.campuslink.modules.admin.user.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An internal staff note about a user.
 *
 * <p><strong>Never exposed on any {@code /api/v1/**} mobile route.</strong> The UI labels the
 * notes thread "Visible to staff only" for the same reason - without it, someone eventually
 * writes a note assuming the user can read it, or assuming they cannot.
 *
 * <p>Kept separate from {@code user_sanctions} deliberately. A note is not an enforcement
 * action: no severity, no expiry, no lift, and it is never shown to the user. Sharing a table
 * with rows the mobile app renders is how an internal note leaks into a user-facing
 * notification.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "admin_user_notes")
@AttributeOverride(name = "id", column = @Column(name = "admin_user_note_id", nullable = false, updatable = false))
public class AdminUserNote extends BaseEntity {

    /** The user the note is about. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The staff member who wrote it. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private AdminUser adminUser;

    @Column(name = "note", columnDefinition = "TEXT", nullable = false)
    private String note;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
