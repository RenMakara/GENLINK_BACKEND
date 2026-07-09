package kh.edu.acleda.campuslink.modules.entertainment.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "badges")
@AttributeOverride(name = "id", column = @Column(name = "badge_id", nullable = false, updatable = false))
public class Badge extends BaseEntity {

    @Column(name = "name_en", nullable = false, length = 180)
    private String nameEn;

    @Column(name = "name_km", length = 180)
    private String nameKm;

    @Column(name = "description_en", columnDefinition = "TEXT")
    private String descriptionEn;

    @Column(name = "description_km", columnDefinition = "TEXT")
    private String descriptionKm;

    @Column(name = "requirement", columnDefinition = "TEXT")
    private String requirement;

    @Column(name = "icon_url", columnDefinition = "TEXT")
    private String iconUrl;

    @Column(name = "deleted_at")
    private Instant deletedAt;

}
