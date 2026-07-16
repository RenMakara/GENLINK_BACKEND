package kh.edu.acleda.campuslink.modules.community.entity;

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

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "hashtags", uniqueConstraints = {@UniqueConstraint(name = "uk_hashtags_tag", columnNames = {"tag"})})
@AttributeOverride(name = "id", column = @Column(name = "hashtag_id", nullable = false, updatable = false))
public class Hashtag extends BaseEntity {

    @Column(name = "tag", nullable = false, length = 100)
    private String tag;

    @Column(name = "usage_count", nullable = false)
    private Integer usageCount;

}
