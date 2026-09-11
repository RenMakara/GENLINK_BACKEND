package kh.edu.acleda.campuslink.modules.notification.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** A user's notifications, newest first. */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** Only the unread ones, newest first. */
    Page<Notification> findByUserIdAndReadStatusFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** Owner-scoped fetch: another user's id resolves to empty, never their row. */
    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    /** Badge count. */
    long countByUserIdAndReadStatusFalse(UUID userId);

    /** Fast duplicate check for retryable or scheduled notification requests. */
    boolean existsByUserIdAndDedupeKey(UUID userId, String dedupeKey);

    /** Mark every unread notification for the user as read in one statement; returns rows touched. */
    @Modifying
    @Query("UPDATE Notification n SET n.readStatus = true, n.readAt = :now "
        + "WHERE n.user.id = :userId AND n.readStatus = false")
    int markAllReadForUser(@Param("userId") UUID userId, @Param("now") LocalDateTime now);
}
