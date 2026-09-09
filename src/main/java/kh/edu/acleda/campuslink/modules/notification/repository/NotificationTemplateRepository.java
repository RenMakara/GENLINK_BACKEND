package kh.edu.acleda.campuslink.modules.notification.repository;

import kh.edu.acleda.campuslink.modules.notification.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    Optional<NotificationTemplate> findByTemplateKey(String templateKey);
}
