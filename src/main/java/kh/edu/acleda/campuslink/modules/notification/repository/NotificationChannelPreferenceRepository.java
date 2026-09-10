package kh.edu.acleda.campuslink.modules.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationChannelPreference;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationChannelPreferenceRepository
    extends JpaRepository<NotificationChannelPreference, UUID> {

    Optional<NotificationChannelPreference> findByUserIdAndChannel(
        UUID userId, NotificationChannel channel);

    List<NotificationChannelPreference> findByUserIdOrderByChannelAsc(UUID userId);
}
