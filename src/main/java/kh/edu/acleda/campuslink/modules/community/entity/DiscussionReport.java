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
 * Backs forum_discussions.report_count. The reporter is recorded so moderators can
 * see who flagged what, and so the unique constraint can make the count mean
 * "distinct people" rather than "taps".
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "discussion_reports",
    uniqueConstraints = {@UniqueConstraint(name = "uk_discussion_reports_discussion_user",
        columnNames = {"discussion_id", "user_id"})})
@AttributeOverride(name = "id", column = @Column(name = "discussion_report_id", nullable = false, updatable = false))
public class DiscussionReport extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discussion_id", nullable = false)
    private ForumDiscussion discussion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "reason", nullable = false, length = 80)
    private String reason;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

}
