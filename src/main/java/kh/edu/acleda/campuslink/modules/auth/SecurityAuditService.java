package kh.edu.acleda.campuslink.modules.auth;

import kh.edu.acleda.campuslink.modules.auth.entity.SecurityAuditLog;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.security.RequestMetadataUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAuditService {

    private final SecurityAuditWriter auditWriter;

    public void log(String eventType, String email, String status, String details, String deviceId) {
        boolean success = "SUCCESS".equalsIgnoreCase(status);
        safeLog(null, email, eventType, success, null, details, deviceId);
    }

    public void logSuccess(String eventType, User user, String email) {
        safeLog(user, email, eventType, true, null, null, null);
    }

    public void logFailure(String eventType, User user, String email, String failureReason) {
        safeLog(user, email, eventType, false, failureReason, null, null);
    }

    public void logFailure(String eventType, String email, String failureReason) {
        safeLog(null, email, eventType, false, failureReason, null, null);
    }

    /**
     * Failures are written in their own transaction, successes join the caller's.
     *
     * <p>A caller logs a failure and then throws, which rolls its transaction back. If the
     * audit row shared that transaction it would be discarded - losing exactly the events
     * the log exists for (failed logins, wrong current password, refresh reuse). A success,
     * by contrast, must not outlive a rollback of the operation it describes.
     */
    private void safeLog(User user, String email, String eventType, boolean success,
                         String failureReason, String details, String deviceId) {
        try {
            SecurityAuditLog entry = buildEntry(user, email, eventType, success, failureReason, details, deviceId);
            if (success) {
                auditWriter.writeInCurrentTransaction(entry);
            } else {
                auditWriter.writeInNewTransaction(entry);
            }
        } catch (Exception ex) {
            // Auditing must never break the operation being audited.
            log.warn("Security audit logging failed for event {} and email {}: {}", eventType, email, ex.getMessage());
        }
    }

    private SecurityAuditLog buildEntry(User user, String email, String eventType, boolean success,
                                        String failureReason, String details, String deviceId) {
        SecurityAuditLog entry = new SecurityAuditLog();
        entry.setUser(user);
        entry.setEventType(eventType);
        entry.setEmail(email);
        entry.setStatus(success ? "SUCCESS" : "FAILED");
        entry.setSuccess(success);
        entry.setFailureReason(failureReason);
        entry.setDetails(details);
        entry.setDeviceId(deviceId);
        entry.setIpAddress(RequestMetadataUtil.getCurrentClientIp());
        entry.setUserAgent(RequestMetadataUtil.getCurrentUserAgent());
        return entry;
    }
}
