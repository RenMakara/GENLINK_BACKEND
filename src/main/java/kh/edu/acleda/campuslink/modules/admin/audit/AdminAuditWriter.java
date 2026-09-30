package kh.edu.acleda.campuslink.modules.admin.audit;

import kh.edu.acleda.campuslink.modules.admin.audit.entity.AdminAuditLog;
import kh.edu.acleda.campuslink.modules.admin.audit.repository.AdminAuditLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists admin audit rows, choosing the transaction they belong to. */
@Component
public class AdminAuditWriter {

    private final AdminAuditLogRepository repository;

    public AdminAuditWriter(AdminAuditLogRepository repository) {
        this.repository = repository;
    }

    /** Survives the caller rolling back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeInNewTransaction(AdminAuditLog entry) {
        repository.save(entry);
    }

    /** Joins the caller's transaction. */
    public void writeInCurrentTransaction(AdminAuditLog entry) {
        repository.save(entry);
    }
}
