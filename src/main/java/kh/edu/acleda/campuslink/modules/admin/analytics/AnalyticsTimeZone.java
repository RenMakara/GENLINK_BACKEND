package kh.edu.acleda.campuslink.modules.admin.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The one place the admin analytics timezone is expressed.
 *
 * <p>Project strategy (SSOT §9.5, verified against the running database): the database stores
 * and the API transmits <strong>UTC</strong>; business logic and every displayed figure are
 * <strong>Asia/Phnom_Penh</strong>. So every aggregate has to convert before it groups.
 *
 * <p>Cambodia is UTC+7. A series bucketed by UTC day is wrong for the first seven hours of
 * every working day: staff arrive at 08:00 local - 01:00 UTC - to a counter already rolled
 * over into a day that has not started.
 *
 * <h2>The mistake this class exists to prevent</h2>
 *
 * The timestamp columns are {@code TIMESTAMP} - timezone-<em>naive</em>, holding UTC. Converting
 * one to local time therefore takes <strong>two</strong> {@code AT TIME ZONE} clauses:
 *
 * <pre>{@code created_at AT TIME ZONE 'UTC' AT TIME ZONE 'Asia/Phnom_Penh'}</pre>
 *
 * The first reads the naive value as UTC and yields a {@code timestamptz}; the second converts
 * that instant to local time. Writing only the second clause - which reads naturally and looks
 * right - does the <em>opposite</em>: it declares the stored value to already be Phnom Penh
 * local. The result is a silent seven-hour shift in every series, with no error and nothing
 * obviously wrong in the output. An earlier draft of the design specified exactly that.
 *
 * <p>Hence: no query hand-rolls the conversion. They call {@link #dayBucket(String)} and its
 * siblings, and the expression exists once.
 */
public final class AnalyticsTimeZone {

    /** Cambodia. Matches the container's {@code TZ}, so Java and SQL agree on "today". */
    public static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");

    private static final String ZONE_SQL = "Asia/Phnom_Penh";

    private AnalyticsTimeZone() {
    }

    /**
     * Converts a naive-UTC column to Phnom Penh local time.
     *
     * @param column the timestamp column, e.g. {@code created_at} or {@code u.created_at}.
     */
    public static String toLocal(String column) {
        return "(" + column + " AT TIME ZONE 'UTC' AT TIME ZONE '" + ZONE_SQL + "')";
    }

    /** Converts a naive UTC timestamp to a PostgreSQL instant for an ISO-8601 API field. */
    public static String toUtcInstant(String column) {
        return "(" + column + " AT TIME ZONE 'UTC')";
    }

    /** Groups by Cambodian calendar day. */
    public static String dayBucket(String column) {
        return "date_trunc('day', " + toLocal(column) + ")";
    }

    /** Groups by Cambodian calendar week. */
    public static String weekBucket(String column) {
        return "date_trunc('week', " + toLocal(column) + ")";
    }

    /** Groups by Cambodian calendar month. */
    public static String monthBucket(String column) {
        return "date_trunc('month', " + toLocal(column) + ")";
    }

    /** Today, in Cambodia, regardless of the JVM's default zone. */
    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /**
     * The instant a Cambodian calendar day begins.
     *
     * <p>The frontend sends local calendar dates; the query needs UTC instants. Converting at
     * this boundary is what keeps the rest of the stack in UTC.
     */
    public static Instant startOfDay(LocalDate localDate) {
        return localDate.atStartOfDay(ZONE).toInstant();
    }

    /**
     * The exclusive end of a local day - i.e. the start of the next one.
     *
     * <p>Ranges are half-open, {@code [start, end)}. The alternative, an inclusive end at
     * 23:59:59, silently drops everything in the final second and has to pick a precision;
     * the finance repositories already use half-open ranges for the same reason.
     */
    public static Instant endOfDayExclusive(LocalDate localDate) {
        return localDate.plusDays(1).atStartOfDay(ZONE).toInstant();
    }

    /** Which Cambodian calendar day an instant falls on. */
    public static LocalDate toLocalDate(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }
}
