package kh.edu.acleda.campuslink.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class RequestMetadataUtil {

    private RequestMetadataUtil() {
    }

    /**
     * The caller's address, as trusted by rate limiting and the audit trail.
     *
     * <h2>X-Real-IP is consulted first, and the order is the point</h2>
     *
     * This used to read {@code X-Forwarded-For} first. That header is a list a client can
     * prepend to, and {@code deploy/nginx/genlink.conf} appended to it rather than overwriting
     * it, so the first entry - the one taken here - was whatever the caller sent. Since
     * {@code AuthRateLimitService} buckets by client IP, rotating that header produced a fresh
     * bucket per request and the 5-failures-per-15-minutes lockout on admin login (SSOT §10.6)
     * never fired. It also let anyone write arbitrary addresses into
     * {@code security_audit_logs}, which is worse: an investigation would chase them.
     *
     * <p>nginx sets {@code X-Real-IP} from {@code $remote_addr} with an unconditional
     * overwrite, so unlike the forwarded chain it cannot be forged from outside. The nginx
     * config has been fixed to overwrite {@code X-Forwarded-For} too; preferring
     * {@code X-Real-IP} here means the application stays correct even if that change is
     * reverted or a differently-configured proxy is introduced.
     *
     * <p><strong>{@code getRemoteAddr()} is not a safe fallback either.</strong> With
     * {@code SERVER_FORWARD_HEADERS_STRATEGY=framework}, Spring's {@code ForwardedHeaderFilter}
     * rewrites the remote address from {@code X-Forwarded-For} before this code runs. It stays
     * last because with no proxy in front - a local run, or a direct connection - it is the
     * only thing there is.
     *
     * @return the client address, or null when there is no request in scope.
     */
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
            // Still the first entry: with the corrected nginx config the header holds exactly
            // one address. Behind a proxy that appends, this is only as trustworthy as that
            // proxy - which is why X-Real-IP is preferred above.
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
