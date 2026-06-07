package kh.edu.acleda.campuslink.modules.finance.entity;

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
@Table(name = "financial_lessons")
@AttributeOverride(name = "id", column = @Column(name = "lesson_id", nullable = false, updatable = false))
public class FinancialLesson extends BaseEntity {

    @Column(name = "title_en", nullable = false, length = 180)
    private String titleEn;

    @Column(name = "title_km", length = 180)
    private String titleKm;

    @Column(name = "content_en", nullable = false, columnDefinition = "TEXT")
    private String contentEn;

    @Column(name = "content_km", columnDefinition = "TEXT")
    private String contentKm;

    @Column(name = "topic", length = 80)
    private String topic;

    @Column(name = "content_type", length = 50)
    private String contentType;

    @Column(name = "estimated_time")
    private Integer estimatedTime;

    @Column(name = "xp_reward", nullable = false)
    private Integer xpReward;

    @Column(name = "deleted_at")
    private Instant deletedAt;

}
