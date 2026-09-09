package kh.edu.acleda.campuslink.modules.profile.entity;

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
import java.time.LocalTime;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.enums.Theme;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_settings", uniqueConstraints = {@UniqueConstraint(name = "uk_user_settings_user", columnNames = {"user_id"})})
@AttributeOverride(name = "id", column = @Column(name = "settings_id", nullable = false, updatable = false))
public class UserSetting extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 20)
    private Language language;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme", nullable = false, length = 30)
    private Theme theme;

    @Column(name = "notification_enabled", nullable = false)
    private Boolean notificationEnabled;

    @Column(name = "notification_quiet_hours_enabled", nullable = false)
    private Boolean notificationQuietHoursEnabled;

    @Column(name = "notification_quiet_start")
    private LocalTime notificationQuietStart;

    @Column(name = "notification_quiet_end")
    private LocalTime notificationQuietEnd;

    @Column(name = "privacy_profile", length = 50)
    private String privacyProfile;

    @Column(name = "allow_messages", nullable = false)
    private Boolean allowMessages;

    @Column(name = "allow_follow_request", nullable = false)
    private Boolean allowFollowRequest;

    @Column(name = "biometric_enabled", nullable = false)
    private Boolean biometricEnabled;

}
