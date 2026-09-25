package kh.edu.acleda.campuslink.modules.admin.analytics.dto.request;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import kh.edu.acleda.campuslink.modules.admin.analytics.AnalyticsTimeZone;
import kh.edu.acleda.campuslink.shared.exception.ApiException;

/** Validated date window shared by every admin analytics endpoint. */
public record AdminAnalyticsQuery(
    LocalDate from,
    LocalDate to,
    Granularity granularity
) {

    /** Resolve the HTTP query values at the request boundary, before a repository is called. */
    public static AdminAnalyticsQuery fromRequest(
        LocalDate from,
        LocalDate to,
        String rawGranularity) {
        LocalDate effectiveTo = to == null ? AnalyticsTimeZone.today() : to;
        LocalDate effectiveFrom = from == null ? effectiveTo.minusDays(29) : from;

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw invalid("from must be on or before to");
        }

        return new AdminAnalyticsQuery(
            effectiveFrom,
            effectiveTo,
            Granularity.fromValue(rawGranularity));
    }

    /** Exclusive UTC boundary for the requested Phnom Penh end date. */
    public java.time.Instant fromInstant() {
        return AnalyticsTimeZone.startOfDay(from);
    }

    /** Exclusive UTC boundary after the requested Phnom Penh end date. */
    public java.time.Instant toExclusiveInstant() {
        return AnalyticsTimeZone.endOfDayExclusive(to);
    }

    /** First bucket represented by the request. Weeks start on Monday in PostgreSQL. */
    public LocalDate bucketFrom() {
        return switch (granularity) {
            case DAY -> from;
            case WEEK -> from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> from.withDayOfMonth(1);
        };
    }

    /** Last bucket represented by the request. */
    public LocalDate bucketTo() {
        return switch (granularity) {
            case DAY -> to;
            case WEEK -> to.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> to.withDayOfMonth(1);
        };
    }

    /** Whitelisted SQL interval literal used only after the enum has been parsed. */
    public String sqlInterval() {
        return switch (granularity) {
            case DAY -> "INTERVAL '1 day'";
            case WEEK -> "INTERVAL '1 week'";
            case MONTH -> "INTERVAL '1 month'";
        };
    }

    public String wireGranularity() {
        return granularity.value;
    }

    private static ApiException invalid(String message) {
        return new ApiException(message, "VALIDATION_ERROR");
    }

    public enum Granularity {
        DAY("day"),
        WEEK("week"),
        MONTH("month");

        private final String value;

        Granularity(String value) {
            this.value = value;
        }

        public static Granularity fromValue(String raw) {
            if (raw == null || raw.isBlank()) {
                return DAY;
            }

            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            for (Granularity candidate : values()) {
                if (candidate.value.equals(normalized)) {
                    return candidate;
                }
            }
            throw invalid("granularity must be one of day, week, or month");
        }
    }
}

