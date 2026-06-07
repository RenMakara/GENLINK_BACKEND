package kh.edu.acleda.campuslink.modules.productivity.entity;

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
@Table(name = "motivation_contents")
@AttributeOverride(name = "id", column = @Column(name = "motivation_id", nullable = false, updatable = false))
public class MotivationContent extends BaseEntity {

    @Column(name = "title_en", nullable = false, length = 180)
    private String titleEn;

    @Column(name = "title_km", length = 180)
    private String titleKm;

    @Column(name = "content_en", nullable = false, columnDefinition = "TEXT")
    private String contentEn;

    @Column(name = "content_km", columnDefinition = "TEXT")
    private String contentKm;

    @Column(name = "category", length = 80)
    private String category;

    @Column(name = "saved_count", nullable = false)
    private Integer savedCount;

    @Column(name = "deleted_at")
    private Instant deletedAt;

}
