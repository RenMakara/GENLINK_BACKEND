package kh.edu.acleda.campuslink.modules.notification.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A user's explicit on/off override for one stable notification type. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "notification_preferences",
    uniqueConstraints = {@UniqueConstraint(
        name = "uk_notification_preferences_user_type",
        columnNames = {"user_id", "notification_type"})})
@AttributeOverride(name = "id", column = @Column(
    name = "preference_id", nullable = false, updatable = false))
public class NotificationPreference extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "notification_type", nullable = false, length = 80)
    private String notificationType;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;
}
