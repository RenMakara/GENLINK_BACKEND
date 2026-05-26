package kh.edu.acleda.campuslink.modules.auth;

import jakarta.servlet.http.HttpServletRequest;
import kh.edu.acleda.campuslink.modules.auth.entity.SecurityAuditLog;
import kh.edu.acleda.campuslink.modules.auth.repository.SecurityAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class SecurityAuditService {

    private final SecurityAuditLogRepository auditLogRepository;

    public void log(String eventType, String email, String status, String details, String deviceId) {
        SecurityAuditLog log = new SecurityAuditLog();
        log.setEventType(eventType);
        log.setEmail(email);
        log.setStatus(status);
        log.setDetails(details);
        log.setDeviceId(deviceId);
        log.setIpAddress(getClientIp());
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
