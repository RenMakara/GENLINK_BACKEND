package kh.edu.acleda.campuslink.modules.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kh.edu.acleda.campuslink.modules.auth.entity.SecurityAuditLog;
import kh.edu.acleda.campuslink.modules.auth.repository.SecurityAuditLogRepository;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Detects a new login context without making authentication depend on notification storage. */
@Slf4j
@Service
public class SuspiciousLoginNotifierImpl implements SuspiciousLoginNotifier {

    static final String LOGIN_EVENT = "LOGIN_SUCCESS";
    static final String NOTIFICATION_TEMPLATE = "SECURITY_SUSPICIOUS_LOGIN";
    static final String NOTIFICATION_TYPE = "SECURITY_SUSPICIOUS_LOGIN";

    private final SecurityAuditLogRepository securityAuditLogRepository;
    private final NotificationEmitter notificationEmitter;

    public SuspiciousLoginNotifierImpl(SecurityAuditLogRepository securityAuditLogRepository,
                                       NotificationEmitter notificationEmitter) {
        this.securityAuditLogRepository = securityAuditLogRepository;
        this.notificationEmitter = notificationEmitter;
    }

    @Override
    public void notifyIfNeeded(User user, String ipAddress, String userAgent) {
        if (user == null || user.getId() == null || isBlank(ipAddress) || isBlank(userAgent)) {
            return;
        }

        String currentIp = ipAddress.strip();
        String currentUserAgent = userAgent.strip();
        try {
            List<SecurityAuditLog> previousLogins = securityAuditLogRepository
                .findTop50ByUserAndEventTypeAndSuccessTrueOrderByCreatedAtDesc(user, LOGIN_EVENT);

            // Do not alert on a first login or after a deployment where there is no complete
            // historical fingerprint yet. That avoids treating missing legacy metadata as risk.
            boolean hasComparableBaseline = previousLogins.stream().anyMatch(this::hasContext);
            if (!hasComparableBaseline || previousLogins.stream()
                .anyMatch(entry -> matchesContext(entry, currentIp, currentUserAgent))) {
                return;
            }

            notificationEmitter.emit(new NotificationRequest(
                user.getId(),
                NOTIFICATION_TEMPLATE,
                NOTIFICATION_TYPE,
                Map.of("loginLocation", "IP address " + currentIp),
                "SECURITY",
                user.getId(),
                "suspicious-login:" + fingerprint(currentIp, currentUserAgent),
                Set.of(NotificationChannel.IN_APP, NotificationChannel.MOBILE_PUSH, NotificationChannel.EMAIL)));
        } catch (RuntimeException exception) {
            // A security alert is useful, but it must never turn a valid login into a failure.
            log.warn("Suspicious login notification failed for user {}", user.getId(), exception);
        }
    }

    private boolean hasContext(SecurityAuditLog entry) {
        return entry != null && !isBlank(entry.getIpAddress()) && !isBlank(entry.getUserAgent());
    }

    private boolean matchesContext(SecurityAuditLog entry, String ipAddress, String userAgent) {
        return hasContext(entry)
            && ipAddress.equals(entry.getIpAddress().strip())
            && userAgent.equals(entry.getUserAgent().strip());
    }

    private String fingerprint(String ipAddress, String userAgent) {
        try {
            byte[] input = (ipAddress + "\u0000" + userAgent).getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
