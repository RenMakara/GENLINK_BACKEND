package kh.edu.acleda.campuslink.modules.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    Optional<NotificationPreference> findByUserIdAndNotificationType(
        UUID userId, String notificationType);

    List<NotificationPreference> findByUserIdOrderByNotificationTypeAsc(UUID userId);
}
