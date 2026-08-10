package kh.edu.acleda.campuslink.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns every request a correlation id, publishes it to the logging MDC, and echoes it back
 * to the caller.
 *
 * <p>Accepts an inbound {@code X-Request-ID} so a chain of calls shares one id; generates one
 * when the client sends none. The value reaches the logs as {@code %X{requestId}} and is
 * persisted on {@code admin_audit_logs.request_id}, so an audit row can be tied to the log
 * lines that produced it.
 *
 * <p>Runs at {@link Ordered#HIGHEST_PRECEDENCE} so it wraps the Spring Security chain rather
 * than sitting inside it. A rejected token or a rate-limited request is exactly the kind of
 * event worth correlating, and those never reach a controller.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestId = sanitize(request.getHeader(RequestId.HEADER));
        if (requestId == null) {
            requestId = RequestId.generate();
        }

        MDC.put(RequestId.MDC_KEY, requestId);
        // Echoed so a user reporting a problem can quote an id that appears in our logs.
        response.setHeader(RequestId.HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Mandatory, not tidiness. Tomcat pools threads, so an id left behind is silently
            // adopted by the next unrelated request on this thread - which corrupts the audit
            // trail in the least visible way possible: plausible ids on the wrong rows.
            MDC.remove(RequestId.MDC_KEY);
        }
    }

    /**
     * Reduces a client-supplied header to something safe to log and to store.
     *
     * <p>The header is attacker-controlled. Two things follow from that:
     *
     * <ul>
     *   <li>Control characters are dropped. A newline in a logged value lets a caller forge
     *       whole log lines - inventing errors, or hiding their own activity behind fake
     *       entries. Everything outside {@code [A-Za-z0-9._:-]} goes.
     *   <li>The result is capped at {@link RequestId#MAX_LENGTH}, the width of the
     *       {@code admin_audit_logs.request_id} column. An oversized header would otherwise
     *       surface as a failed insert inside the audit writer.
     * </ul>
     *
     * @return the cleaned value, or null when the header is absent, blank, or contained
     *     nothing usable - in which case the caller generates a fresh id.
     */
    private String sanitize(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        StringBuilder cleaned = new StringBuilder(RequestId.MAX_LENGTH);
        for (int i = 0; i < headerValue.length() && cleaned.length() < RequestId.MAX_LENGTH; i++) {
            char c = headerValue.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '-' || c == '_' || c == '.' || c == ':';
            if (allowed) {
                cleaned.append(c);
            }
        }
        return cleaned.isEmpty() ? null : cleaned.toString();
    }
}
