package kh.edu.acleda.campuslink.modules.profile.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * System-generated content, so names are bilingual columns rather than a
 * translation table - see the language support plan.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "interests", uniqueConstraints = {@UniqueConstraint(name = "uk_interests_code", columnNames = {"code"})})
@AttributeOverride(name = "id", column = @Column(name = "interest_id", nullable = false, updatable = false))
public class Interest extends BaseEntity {

    /** Stable identifier for seeds and client-side icon mapping. */
    @Column(name = "code", nullable = false, length = 60)
    private String code;

    @Column(name = "name_en", nullable = false, length = 80)
    private String nameEn;

    @Column(name = "name_km", nullable = false, length = 120)
    private String nameKm;

    @Column(name = "category", length = 60)
    private String category;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

}
