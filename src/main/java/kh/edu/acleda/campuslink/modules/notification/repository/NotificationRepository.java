package kh.edu.acleda.campuslink.modules.notification.repository;

import kh.edu.acleda.campuslink.modules.notification.entity.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
