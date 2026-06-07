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
import jakarta.persistence.UniqueConstraint;
import kh.edu.acleda.campuslink.modules.entertainment.enums.LeaderboardCategory;
import kh.edu.acleda.campuslink.modules.entertainment.enums.LeaderboardPeriod;
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
@Table(name = "leaderboards", uniqueConstraints = {@UniqueConstraint(name = "uk_leaderboards_user_category_period", columnNames = {"user_id", "category", "period"})})
@AttributeOverride(name = "id", column = @Column(name = "leaderboard_id", nullable = false, updatable = false))
public class Leaderboard extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private LeaderboardCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "period", nullable = false, length = 50)
    private LeaderboardPeriod period;

    @Column(name = "rank", nullable = false)
    private Integer rank;

    @Column(name = "points", nullable = false)
    private Integer points;

}
