package kh.edu.acleda.campuslink.modules.productivity.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByUserIdAndCompletedFalseAndDeletedAtIsNull(UUID userId);

    long countByUserIdAndDeletedAtIsNull(UUID userId);

    long countByUserIdAndDeletedAtIsNullAndCompletedTrue(UUID userId);

    Optional<Task> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    /** Incomplete first, then earliest due date (Postgres sorts NULL due dates last). */
    List<Task> findByUserIdAndDeletedAtIsNullOrderByCompletedAscDueDateAsc(UUID userId);

    /** Counts the dashboard summary in the database instead of loading every task. */
    @Query("""
        SELECT new kh.edu.acleda.campuslink.modules.productivity.repository.TaskDashboardCounts(
            COUNT(t),
            COALESCE(SUM(CASE WHEN t.completed = true THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN t.dueDate >= :todayStart AND t.dueDate < :tomorrowStart
                THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN t.completed = false AND t.dueDate < :now
                THEN 1 ELSE 0 END), 0)
        )
        FROM Task t
        WHERE t.user.id = :userId AND t.deletedAt IS NULL
        """)
    TaskDashboardCounts summarizeForDashboard(
        @Param("userId") UUID userId,
        @Param("todayStart") LocalDateTime todayStart,
        @Param("tomorrowStart") LocalDateTime tomorrowStart,
        @Param("now") LocalDateTime now);

    /** Incomplete, live tasks whose configured reminder falls inside the scheduler window. */
    @Query("SELECT t FROM Task t JOIN FETCH t.user "
        + "WHERE t.completed = false AND t.deletedAt IS NULL "
        + "AND t.reminderTime >= :from AND t.reminderTime < :to "
        + "ORDER BY t.reminderTime ASC")
    List<Task> findForReminder(@Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to);
}
