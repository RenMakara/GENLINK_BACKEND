package kh.edu.acleda.campuslink.modules.productivity.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kh.edu.acleda.campuslink.modules.productivity.entity.FocusSessionStatus;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.entity.FocusSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface FocusSessionRepository extends JpaRepository<FocusSession, UUID> {

    Optional<FocusSession> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM FocusSession f WHERE f.id = :id AND f.user.id = :userId")
    Optional<FocusSession> findByIdAndUserIdForUpdate(
        @Param("id") UUID id, @Param("userId") UUID userId);

    Optional<FocusSession> findFirstByUserIdAndStatusInOrderByStartedAtDesc(
        UUID userId, Collection<FocusSessionStatus> statuses);

    Page<FocusSession> findByUserIdAndCompletedTrueOrderByStartedAtDesc(
        UUID userId, Pageable pageable);

    /** Completed sessions, for the stats aggregate. */
    List<FocusSession> findByUserIdAndCompletedTrue(UUID userId);

    /** Aggregates the dashboard summary without materializing all completed sessions. */
    @Query("""
        SELECT new kh.edu.acleda.campuslink.modules.productivity.repository.FocusDashboardSummary(
            COALESCE(SUM(CASE WHEN f.startedAt >= :todayStart AND f.startedAt < :tomorrowStart
                THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN f.startedAt >= :todayStart AND f.startedAt < :tomorrowStart
                THEN f.focusedSeconds ELSE 0 END), 0),
            COALESCE(SUM(f.xpEarned), 0)
        )
        FROM FocusSession f
        WHERE f.user.id = :userId AND f.completed = true
        """)
    FocusDashboardSummary summarizeForDashboard(
        @Param("userId") UUID userId,
        @Param("todayStart") LocalDateTime todayStart,
        @Param("tomorrowStart") LocalDateTime tomorrowStart);
}
