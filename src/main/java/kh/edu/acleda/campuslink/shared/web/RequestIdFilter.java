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

/** Assigns every request a correlation id, publishes it to the logging MDC, and echoes it back to the caller. */
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
            // Mandatory, not tidiness.
            MDC.remove(RequestId.MDC_KEY);
        }
    }

    /** Reduces a client-supplied header to something safe to log and to store. */
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
