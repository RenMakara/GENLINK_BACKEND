package kh.edu.acleda.campuslink.modules.admin.analytics.dto.response;

import java.time.LocalDate;
import java.util.List;

/** Read-only response records for the fixed admin analytics dashboards. */
public final class AdminAnalyticsResponse {

    private AdminAnalyticsResponse() {
    }

    public record UserGrowth(
        LocalDate from,
        LocalDate to,
        String granularity,
        List<SeriesPoint> points
    ) {
    }

    public record ActiveUsers(
        LocalDate from,
        LocalDate to,
        String granularity,
        boolean estimated,
        List<ActiveUsersPoint> points
    ) {
    }

    public record Registrations(
        LocalDate from,
        LocalDate to,
        String granularity,
        List<RegistrationPoint> points
    ) {
    }

    /** Used by status, university, work-status, language and platform dashboards. */
    public record Distribution(
        LocalDate from,
        LocalDate to,
        String granularity,
        List<DistributionPoint> distribution,
        List<TrendPoint> trend
    ) {
    }

    public record XpDistribution(
        LocalDate from,
        LocalDate to,
        String granularity,
        List<DistributionPoint> buckets,
        List<LevelPoint> levels
    ) {
    }

    public record SeriesPoint(LocalDate bucket, long count) {
    }

    public record ActiveUsersPoint(LocalDate bucket, long dau, long mau) {
    }

    public record RegistrationPoint(
        LocalDate bucket,
        long local,
        long google,
        long other,
        long total
    ) {
    }

    public record DistributionPoint(String label, long count) {
    }

    public record TrendPoint(LocalDate bucket, String label, long count) {
    }

    public record LevelPoint(int level, long count) {
    }
}

