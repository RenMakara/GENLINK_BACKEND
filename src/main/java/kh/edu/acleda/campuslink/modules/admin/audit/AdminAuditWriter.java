package kh.edu.acleda.campuslink.modules.admin.audit;

import kh.edu.acleda.campuslink.modules.admin.audit.entity.AdminAuditLog;
import kh.edu.acleda.campuslink.modules.admin.audit.repository.AdminAuditLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists admin audit rows, choosing the transaction they belong to.
 *
 * <p>A separate bean from {@link AdminAuditService}, for two reasons that are easy to get
 * wrong:
 *
 * <ul>
 *   <li>{@code @Transactional} only applies through the proxy, so a propagation boundary on a
 *       method called from within the same class does nothing at all.
 *   <li>It lets the service wrap the call in a try/catch from <em>outside</em> the transaction.
 *       Catching the exception inside the boundary would leave the transaction marked
 *       rollback-only, and the caller would then fail at commit with an
 *       {@code UnexpectedRollbackException} - turning a swallowed audit failure into a broken
 *       request.
 * </ul>
 *
 * <p>Deliberately mirrors {@code SecurityAuditWriter}, which exists in this exact shape because
 * the codebase already got this wrong once.
 */
@Component
public class AdminAuditWriter {

    private final AdminAuditLogRepository repository;

    public AdminAuditWriter(AdminAuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Survives the caller rolling back. Used for failures.
     *
     * <p>A failing admin action logs and then throws, which rolls its transaction back. Sharing
     * that transaction would discard the row - losing precisely the events the log exists for.
     * This is the bug {@code SecurityAuditService} shipped with: failed logins and
     * wrong-password attempts were never recorded, because the audit row died with the throw.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeInNewTransaction(AdminAuditLog entry) {
        repository.save(entry);
    }

    /**
     * Joins the caller's transaction. Used for successes.
     *
     * <p>The mirror-image danger: an action recorded as SUCCESS whose transaction then rolls
     * back would leave the log asserting something that never happened. A success must not
     * outlive the operation it describes, so it shares its fate.
     */
    public void writeInCurrentTransaction(AdminAuditLog entry) {
        repository.save(entry);
    }
}
