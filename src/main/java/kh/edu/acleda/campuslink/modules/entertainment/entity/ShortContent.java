package kh.edu.acleda.campuslink.modules.entertainment.entity;

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
import kh.edu.acleda.campuslink.modules.user.entity.User;
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
@Table(name = "short_contents")
@AttributeOverride(name = "id", column = @Column(name = "short_content_id", nullable = false, updatable = false))
public class ShortContent extends BaseEntity {

    @Column(name = "title_en", nullable = false, length = 180)
    private String titleEn;

    @Column(name = "title_km", length = 180)
    private String titleKm;

    @Column(name = "description_en", columnDefinition = "TEXT")
    private String descriptionEn;

    @Column(name = "description_km", columnDefinition = "TEXT")
    private String descriptionKm;

    @Column(name = "category", length = 80)
    private String category;

    @Column(name = "video_url", columnDefinition = "TEXT")
    private String videoUrl;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(name = "like_count", nullable = false)
    private Integer likeCount;

    @Column(name = "save_count", nullable = false)
    private Integer saveCount;

    @Column(name = "share_count", nullable = false)
    private Integer shareCount;

    @Column(name = "deleted_at")
    private Instant deletedAt;

}
