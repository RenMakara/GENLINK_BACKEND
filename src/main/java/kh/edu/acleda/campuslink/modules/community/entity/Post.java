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
import kh.edu.acleda.campuslink.modules.community.enums.PostType;
import kh.edu.acleda.campuslink.modules.community.enums.Visibility;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import kh.edu.acleda.campuslink.shared.enums.Language;
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
@Table(name = "posts")
@AttributeOverride(name = "id", column = @Column(name = "post_id", nullable = false, updatable = false))
public class Post extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id")
    private Community community;

    @Column(name = "content_text", columnDefinition = "TEXT")
    private String contentText;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_type", nullable = false, length = 50)
    private PostType postType;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 50)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", length = 20)
    private Language language;

    @Column(name = "like_count", nullable = false)
    private Integer likeCount;

    @Column(name = "comment_count", nullable = false)
    private Integer commentCount;

    @Column(name = "share_count", nullable = false)
    private Integer shareCount;

    @Column(name = "save_count", nullable = false)
    private Integer saveCount;

    @Column(name = "report_count", nullable = false)
    private Integer reportCount;

    @Column(name = "deleted_at")
    private Instant deletedAt;

}
