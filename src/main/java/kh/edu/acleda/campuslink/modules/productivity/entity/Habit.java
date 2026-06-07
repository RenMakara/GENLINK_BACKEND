package kh.edu.acleda.campuslink.modules.productivity.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
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
@Table(name = "habits")
@AttributeOverride(name = "id", column = @Column(name = "habit_id", nullable = false, updatable = false))
public class Habit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "category", length = 80)
    private String category;

    @Column(name = "frequency", length = 50)
    private String frequency;

    @Column(name = "reminder_time")
    private LocalTime reminderTime;

    @Column(name = "streak_count", nullable = false)
    private Integer streakCount;

    @Column(name = "completion_rate", precision = 5, scale = 2)
    private BigDecimal completionRate;

    @Column(name = "deleted_at")
    private Instant deletedAt;

}
