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
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
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
@Table(name = "notification_templates", uniqueConstraints = {@UniqueConstraint(name = "uk_notification_templates_key", columnNames = {"template_key"})})
@AttributeOverride(name = "id", column = @Column(name = "template_id", nullable = false, updatable = false))
public class NotificationTemplate extends BaseEntity {

    @Column(name = "template_key", nullable = false, length = 120)
    private String templateKey;

    @Column(name = "title_en", nullable = false, length = 180)
    private String titleEn;

    @Column(name = "title_km", length = 180)
    private String titleKm;

    @Column(name = "message_en", nullable = false, columnDefinition = "TEXT")
    private String messageEn;

    @Column(name = "message_km", columnDefinition = "TEXT")
    private String messageKm;

}
