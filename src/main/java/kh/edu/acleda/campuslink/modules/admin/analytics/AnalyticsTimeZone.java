package kh.edu.acleda.campuslink.modules.admin.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** The one place the admin analytics timezone is expressed. */
public final class AnalyticsTimeZone {

    /** Cambodia. Matches the container's {@code TZ}, so Java and SQL agree on "today". */
    public static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");

    private static final String ZONE_SQL = "Asia/Phnom_Penh";

    private AnalyticsTimeZone() {
    }

    /** Converts a naive-UTC column to Phnom Penh local time. */
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

    /** The instant a Cambodian calendar day begins. */
    public static Instant startOfDay(LocalDate localDate) {
        return localDate.atStartOfDay(ZONE).toInstant();
    }

    /** The exclusive end of a local day - i.e. the start of the next one. */
    public static Instant endOfDayExclusive(LocalDate localDate) {
        return localDate.plusDays(1).atStartOfDay(ZONE).toInstant();
    }

    /** Which Cambodian calendar day an instant falls on. */
    public static LocalDate toLocalDate(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }
}
