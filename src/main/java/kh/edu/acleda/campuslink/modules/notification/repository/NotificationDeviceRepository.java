package kh.edu.acleda.campuslink.modules.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationDevice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeviceRepository extends JpaRepository<NotificationDevice, UUID> {

    Optional<NotificationDevice> findByTokenHash(String tokenHash);

    List<NotificationDevice> findByUserIdAndActiveTrueOrderByLastSeenAtDesc(UUID userId);

    Optional<NotificationDevice> findByIdAndUserId(UUID deviceId, UUID userId);
}
