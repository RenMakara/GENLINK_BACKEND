package kh.edu.acleda.campuslink.modules.admin.audit.repository;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.audit.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Read and append only.
 *
 * <p>{@code JpaRepository} inherits {@code delete*} methods; <strong>they must never be
 * called.</strong> There is no delete endpoint at any permission level, and an audit log an
 * admin can erase is not an audit log. The API surface (ADM-116) exposes no mutation route,
 * which is where this is actually enforced.
 */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {

    /** The dashboard's recent-activity panel. */
    Page<AdminAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Scoped audit reading: MODERATOR and FINANCE_ADMIN see only their own actions. */
    Page<AdminAuditLog> findByAdminUserIdOrderByCreatedAtDesc(UUID adminUserId, Pageable pageable);

    /** Everything done to one target, reached from that user's or post's detail page. */
    Page<AdminAuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
        String targetType, UUID targetId, Pageable pageable);

    /** The rows of one bulk operation, correlated by the batch id the client supplied. */
    List<AdminAuditLog> findByBatchId(UUID batchId);
}
