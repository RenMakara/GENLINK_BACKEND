package kh.edu.acleda.campuslink.shared.web;

import java.util.UUID;
import org.slf4j.MDC;

/** The correlation id for the request being handled on this thread. */
public final class RequestId {

    /** MDC key. Referenced by the logging patterns as {@code %X{requestId}}. */
    public static final String MDC_KEY = "requestId";

    /** Inbound and outbound header. The de facto standard spelling. */
    public static final String HEADER = "X-Request-ID";

    /** Matches the {@code admin_audit_logs.request_id} column width. */
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
