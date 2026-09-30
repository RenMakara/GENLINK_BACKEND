package kh.edu.acleda.campuslink.modules.admin.audit.repository;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.audit.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Read and append only. */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID>,
    JpaSpecificationExecutor<AdminAuditLog> {

    /** Loads the actor and linked user in the same read so audit pages do not create N+1 queries. */
    @Override
    @EntityGraph(attributePaths = {"adminUser", "adminUser.user"})
    Page<AdminAuditLog> findAll(Specification<AdminAuditLog> specification, Pageable pageable);

    /** Loads the full actor projection needed by the audit detail endpoint. */
    @Override
    @EntityGraph(attributePaths = {"adminUser", "adminUser.user"})
    java.util.Optional<AdminAuditLog> findById(UUID id);

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
