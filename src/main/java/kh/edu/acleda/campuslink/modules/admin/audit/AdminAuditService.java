package kh.edu.acleda.campuslink.modules.admin.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.audit.entity.AdminAuditLog;
import kh.edu.acleda.campuslink.modules.admin.audit.enums.AuditStatus;
import kh.edu.acleda.campuslink.modules.admin.rbac.AdminIdentity;
import kh.edu.acleda.campuslink.modules.admin.rbac.AdminPermissionCache;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.shared.security.RequestMetadataUtil;
import kh.edu.acleda.campuslink.shared.web.RequestId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * The way privileged actions get recorded. Every mutating admin endpoint calls this.
 *
 * <p>Fills in the actor, IP, user agent and correlation id from the current request, so call
 * sites only describe what they did. The correlation id ties the row back to the application
 * log lines for the same request, which is most of what makes an audit trail usable during an
 * incident.
 */
@Service
public class AdminAuditService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditService.class);

    private final AdminAuditWriter writer;
    private final AdminUserRepository adminUserRepository;
    private final AdminPermissionCache permissionCache;
    private final ObjectMapper objectMapper;

    public AdminAuditService(AdminAuditWriter writer,
                             AdminUserRepository adminUserRepository,
                             AdminPermissionCache permissionCache,
                             ObjectMapper objectMapper) {
        this.writer = writer;
        this.adminUserRepository = adminUserRepository;
        this.permissionCache = permissionCache;
        this.objectMapper = objectMapper;
    }

    /** Records a successful action. */
    public void recordSuccess(String action, String targetType, UUID targetId, String reason) {
        record(AdminAuditEvent.builder()
            .action(action).targetType(targetType).targetId(targetId)
            .reason(reason).status(AuditStatus.SUCCESS)
            .build());
    }

    /** Records an action that was attempted and refused or failed. */
    public void recordFailure(String action, String targetType, UUID targetId, String reason, String errorCode) {
        record(AdminAuditEvent.builder()
            .action(action).targetType(targetType).targetId(targetId)
            .reason(reason).errorCode(errorCode).status(AuditStatus.FAILURE)
            .build());
    }

    /**
     * Records an event, choosing the transaction by outcome.
     *
     * <p>The try/catch sits outside the writer's transaction boundary on purpose - see
     * {@link AdminAuditWriter}. Auditing must never be the reason an admin action fails, but
     * swallowing the exception inside the boundary would poison the caller's transaction and
     * do exactly that.
     */
    public void record(AdminAuditEvent event) {
        try {
            AdminAuditLog entry = toEntity(event);
            if (event.isFailure()) {
                writer.writeInNewTransaction(entry);
            } else {
                writer.writeInCurrentTransaction(entry);
            }
        } catch (Exception ex) {
            // Losing an audit row is bad; failing the admin action because auditing broke is
            // worse. Logged at error because a silent gap in the trail is not acceptable
            // either - someone has to notice.
            log.error("Failed to write admin audit row for action {} on {} {}: {}",
                event.action(), event.targetType(), event.targetId(), ex.getMessage(), ex);
        }
    }

    /**
     * Serialises a before/after snapshot.
     *
     * <p><strong>Pass a DTO, record or Map - never a JPA entity.</strong> An entity drags in
     * lazy proxies, which either throw outside a session or quietly serialise half the object
     * graph into the audit row.
     *
     * @return the JSON, or null if it could not be serialised - a snapshot is never worth
     *     failing the action for.
     */
    public String toJson(Object snapshot) {
        if (snapshot == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialise audit snapshot of type {}: {}",
                snapshot.getClass().getSimpleName(), ex.getMessage());
            return null;
        }
    }

    private AdminAuditLog toEntity(AdminAuditEvent event) {
        AdminAuditLog entry = new AdminAuditLog();
        entry.setAdminUser(currentAdminReference());
        entry.setAction(event.action());
        entry.setTargetType(event.targetType());
        entry.setTargetId(event.targetId());
        entry.setBeforeJson(event.beforeJson());
        entry.setAfterJson(event.afterJson());
        entry.setReason(event.reason());
        entry.setBatchId(event.batchId());
        entry.setStatus(event.status() == null ? AuditStatus.SUCCESS : event.status());
        entry.setErrorCode(event.errorCode());
        entry.setIpAddress(RequestMetadataUtil.getCurrentClientIp());
        entry.setUserAgent(RequestMetadataUtil.getCurrentUserAgent());
        entry.setRequestId(RequestId.get());
        return entry;
    }

    /**
     * The acting admin, as a reference rather than a loaded entity.
     *
     * <p>The identity lookup is the one already cached for permission resolution, so this adds
     * no query on the request path; {@code getReferenceById} then supplies the foreign key
     * without a second select.
     *
     * @return null when there is no acting admin - the bootstrap runner that creates the first
     *     SUPER_ADMIN, and any failure recorded before authentication completed. The column is
     *     nullable for exactly these cases.
     */
    private kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser currentAdminReference() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            return null;
        }
        AdminIdentity identity = permissionCache.identityFor(authentication.getName());
        if (!identity.isAdmin()) {
            return null;
        }
        return adminUserRepository.getReferenceById(identity.adminUserId());
    }
}
