package kh.edu.acleda.campuslink.modules.notification.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One mobile installation registered to receive offline notifications for a user. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "notification_devices",
    uniqueConstraints = {@UniqueConstraint(
        name = "uk_notification_devices_token_hash",
        columnNames = "token_hash")})
@AttributeOverride(name = "id", column = @Column(
    name = "device_id", nullable = false, updatable = false))
public class NotificationDevice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private NotificationDevicePlatform platform;

    /** SHA-256 of the token, used for idempotent registration without exposing the token. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    /** AES-GCM protected token used only at the FCM provider boundary. */
    @Column(name = "token_ciphertext", nullable = false, columnDefinition = "TEXT")
    private String tokenCiphertext;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "last_failure_at")
    private Instant lastFailureAt;
}
