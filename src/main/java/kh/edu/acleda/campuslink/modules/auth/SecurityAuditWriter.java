package kh.edu.acleda.campuslink.modules.auth;

import kh.edu.acleda.campuslink.modules.auth.entity.SecurityAuditLog;
import kh.edu.acleda.campuslink.modules.auth.repository.SecurityAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists audit entries in a transaction of their own.
 *
 * <p>Separate bean on purpose: the propagation boundary only applies to calls that go
 * through the proxy, and it lets {@link SecurityAuditService} wrap the call in a
 * try/catch from <em>outside</em> the transaction. Swallowing the exception inside the
 * boundary would leave the transaction marked rollback-only and surface as an
 * UnexpectedRollbackException at commit.
 */
@Component
@RequiredArgsConstructor
public class SecurityAuditWriter {

    private final SecurityAuditLogRepository auditLogRepository;

    /** Survives the caller rolling back - used for failures, which always precede a throw. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeInNewTransaction(SecurityAuditLog entry) {
        auditLogRepository.save(entry);
    }

    /** Joins the caller's transaction - used for successes, which must not outlive a rollback. */
    public void writeInCurrentTransaction(SecurityAuditLog entry) {
        auditLogRepository.save(entry);
    }
}
