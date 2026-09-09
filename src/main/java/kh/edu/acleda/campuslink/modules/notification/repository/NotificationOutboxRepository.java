package kh.edu.acleda.campuslink.modules.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationOutbox;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationOutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

    boolean existsByRecipientUserIdAndDedupeKey(UUID recipientUserId, String dedupeKey);

    /**
     * Claims the oldest due rows while holding database row locks. The surrounding transaction
     * keeps the lock until each row has a terminal or retry status.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM NotificationOutbox o "
        + "WHERE o.status = :status AND o.nextAttemptAt <= :now "
        + "ORDER BY o.createdAt ASC")
    List<NotificationOutbox> findDueForUpdate(
        @Param("status") NotificationOutboxStatus status,
        @Param("now") Instant now,
        Pageable pageable);
}
