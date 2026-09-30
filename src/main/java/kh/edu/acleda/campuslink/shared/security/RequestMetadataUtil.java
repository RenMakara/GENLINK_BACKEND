package kh.edu.acleda.campuslink.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class RequestMetadataUtil {

    private RequestMetadataUtil() {
    }

    /** The caller's address, as trusted by rate limiting and the audit trail. */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // Still the first entry: with the corrected nginx config the header holds exactly one address.
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public static String getUserAgent(HttpServletRequest request) {
        return request == null ? null : request.getHeader("User-Agent");
    }

    public static String getDeviceInfo(HttpServletRequest request) {
        String userAgent = getUserAgent(request);
        return userAgent == null || userAgent.isBlank() ? "unknown" : userAgent;
    }

    public static String getCurrentClientIp() {
        return getClientIp(currentRequest());
    }

    public static String getCurrentUserAgent() {
        return getUserAgent(currentRequest());
    }

    public static String getCurrentDeviceInfo() {
        return getDeviceInfo(currentRequest());
    }

    private static HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return attributes.getRequest();
    }
}
