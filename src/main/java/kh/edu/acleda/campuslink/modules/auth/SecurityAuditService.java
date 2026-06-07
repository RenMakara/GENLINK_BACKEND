package kh.edu.acleda.campuslink.modules.auth;

import jakarta.servlet.http.HttpServletRequest;
import kh.edu.acleda.campuslink.modules.auth.entity.SecurityAuditLog;
import kh.edu.acleda.campuslink.modules.auth.repository.SecurityAuditLogRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.security.RequestMetadataUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAuditService {

    private final SecurityAuditLogRepository auditLogRepository;

    public void log(String eventType, String email, String status, String details, String deviceId) {
        safeLog(null, email, eventType, "SUCCESS".equalsIgnoreCase(status), details, null, deviceId);
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

    private void safeLog(User user, String email, String eventType, boolean success, String failureReason, String details, String deviceId) {
        try {
            saveLog(user, email, eventType, success, failureReason, details, deviceId);
        } catch (Exception ex) {
            log.warn("Security audit logging failed for event {} and email {}: {}", eventType, email, ex.getMessage());
        }
    }

    private void saveLog(User user, String email, String eventType, boolean success, String failureReason, String details, String deviceId) {
        SecurityAuditLog log = new SecurityAuditLog();
        log.setUser(user);
        log.setEventType(eventType);
        log.setEmail(email);
        log.setStatus(success ? "SUCCESS" : "FAILED");
        log.setSuccess(success);
        log.setFailureReason(failureReason);
        log.setDetails(details);
        log.setDeviceId(deviceId);
        log.setIpAddress(RequestMetadataUtil.getCurrentClientIp());
        log.setUserAgent(RequestMetadataUtil.getCurrentUserAgent());
        auditLogRepository.save(log);
    }

    private String getClientIp() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
}
