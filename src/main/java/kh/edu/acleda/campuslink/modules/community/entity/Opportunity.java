package kh.edu.acleda.campuslink.modules.community.entity;

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
import java.time.LocalDate;
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
@Table(name = "opportunities")
@AttributeOverride(name = "id", column = @Column(name = "opportunity_id", nullable = false, updatable = false))
public class Opportunity extends BaseEntity {

    @Column(name = "title", nullable = false, length = 180)
    private String title;

    @Column(name = "organization", length = 180)
    private String organization;

    @Column(name = "type", length = 80)
    private String type;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "deadline")
    private LocalDate deadline;

    @Column(name = "location", length = 120)
    private String location;

    @Column(name = "apply_link", columnDefinition = "TEXT")
    private String applyLink;

    @Column(name = "deleted_at")
    private Instant deletedAt;

}
