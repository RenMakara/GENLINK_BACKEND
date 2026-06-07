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
import java.time.LocalDateTime;
import kh.edu.acleda.campuslink.modules.entertainment.entity.Badge;
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
@Table(name = "user_badges", uniqueConstraints = {@UniqueConstraint(name = "uk_user_badges_user_badge", columnNames = {"user_id", "badge_id"})})
@AttributeOverride(name = "id", column = @Column(name = "user_badge_id", nullable = false, updatable = false))
public class UserBadge extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "badge_id", nullable = true)
    private Badge badge;

    @Column(name = "unlocked_at", nullable = false)
    private LocalDateTime unlockedAt;

}
