package kh.edu.acleda.campuslink.modules.auth.repository;

import kh.edu.acleda.campuslink.modules.auth.entity.SecurityAuditLog;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLog, UUID> {

    /** Recent security activity shown to the user in Settings > Security. */
    List<SecurityAuditLog> findTop50ByUserOrderByCreatedAtDesc(User user);

    /** Successful logins with a complete context are the baseline for new-login detection. */
    List<SecurityAuditLog> findTop50ByUserAndEventTypeAndSuccessTrueOrderByCreatedAtDesc(
        User user, String eventType);
}
