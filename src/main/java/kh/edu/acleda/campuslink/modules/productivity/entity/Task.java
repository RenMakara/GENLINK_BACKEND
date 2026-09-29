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
import java.time.Instant;
import java.time.LocalDateTime;
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
@Table(name = "tasks")
@AttributeOverride(name = "id", column = @Column(name = "task_id", nullable = false, updatable = false))
public class Task extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "priority", length = 30)
    private String priority;

    @Column(name = "category", length = 80)
    private String category;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "reminder_time")
    private LocalDateTime reminderTime;

    @Column(name = "completed", nullable = false)
    private Boolean completed;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

}
