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
@Table(name = "forum_discussions")
@AttributeOverride(name = "id", column = @Column(name = "discussion_id", nullable = false, updatable = false))
public class ForumDiscussion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @Column(name = "title", nullable = false, length = 180)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "category", length = 80)
    private String category;

    @Column(name = "is_anonymous", nullable = false)
    private Boolean isAnonymous;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", length = 20)
    private Language language;

    @Column(name = "reaction_count", nullable = false)
    private Integer reactionCount;

    @Column(name = "comment_count", nullable = false)
    private Integer commentCount;

    @Column(name = "report_count", nullable = false)
    private Integer reportCount;

    @Column(name = "deleted_at")
    private Instant deletedAt;

}
