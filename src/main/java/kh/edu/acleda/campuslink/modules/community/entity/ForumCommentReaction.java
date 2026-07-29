package kh.edu.acleda.campuslink.modules.community.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Backs forum_comments.like_count. One row per (comment, user); the unique constraint makes
 * the count mean "distinct people" rather than "taps". Mirrors {@link ForumReaction}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "forum_comment_reactions",
    uniqueConstraints = {@UniqueConstraint(name = "uk_forum_comment_reactions_comment_user",
        columnNames = {"forum_comment_id", "user_id"})})
@AttributeOverride(name = "id",
    column = @Column(name = "forum_comment_reaction_id", nullable = false, updatable = false))
public class ForumCommentReaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forum_comment_id", nullable = false)
    private ForumComment comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "reaction_type", nullable = false, length = 50)
    private String reactionType;

}
