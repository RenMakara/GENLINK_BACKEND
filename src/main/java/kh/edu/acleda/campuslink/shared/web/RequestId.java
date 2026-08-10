package kh.edu.acleda.campuslink.shared.web;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * The correlation id for the request being handled on this thread.
 *
 * <p>Set once per request by {@link RequestIdFilter} and readable from anywhere without
 * threading an {@code HttpServletRequest} through every layer. {@code admin_audit_logs}
 * stores it, so a row in that table can be traced back to the exact application log lines
 * that produced it - which is most of what makes an audit trail useful during an incident.
 *
 * <p><strong>Thread-bound.</strong> MDC lives in a {@code ThreadLocal}, so this returns null
 * on any thread the filter did not touch: {@code @Async} methods, {@code @Scheduled} jobs and
 * the finance/anonymisation batch jobs. That is correct rather than a gap - those have no
 * request to correlate to - but code that moves work onto another thread must carry the value
 * across explicitly if it wants it.
 */
public final class RequestId {

    /** MDC key. Referenced by the logging patterns as {@code %X{requestId}}. */
    public static final String MDC_KEY = "requestId";

    /** Inbound and outbound header. The de facto standard spelling. */
    public static final String HEADER = "X-Request-ID";

    /**
     * Matches the {@code admin_audit_logs.request_id} column width. A client-supplied value
     * longer than this is truncated rather than rejected: a bad header should not fail a
     * request, and it must never overflow the insert.
     */
    public static final int MAX_LENGTH = 64;

    private RequestId() {
    }

    /** @return the current request's id, or null outside a request (jobs, async, tests). */
    public static String get() {
        return MDC.get(MDC_KEY);
    }

    /** Generates a fresh id. Plain UUID: what callers and log aggregators expect to see. */
    public static String generate() {
        return UUID.randomUUID().toString();
    }
}
