package kh.edu.acleda.campuslink.modules.admin.analytics.repository;

import java.time.LocalDate;
import java.util.List;
import kh.edu.acleda.campuslink.modules.admin.analytics.dto.request.AdminAnalyticsQuery;

/** Read-only aggregate boundary for analytics endpoints. */
public interface AdminAnalyticsQueryRepository {

    List<SeriesRow> findUserGrowth(AdminAnalyticsQuery query);

    List<ActiveUsersRow> findActiveUsers(AdminAnalyticsQuery query);

    List<RegistrationRow> findRegistrations(AdminAnalyticsQuery query);

    DistributionResult findUserStatus(AdminAnalyticsQuery query);

    List<DistributionRow> findUniversities(AdminAnalyticsQuery query);

    List<DistributionRow> findWorkStatus(AdminAnalyticsQuery query);

    XpDistributionResult findXpDistribution(AdminAnalyticsQuery query);

    DistributionResult findLanguages(AdminAnalyticsQuery query);

    List<DistributionRow> findPlatforms(AdminAnalyticsQuery query);

    record SeriesRow(LocalDate bucket, long count) {
    }

    record ActiveUsersRow(LocalDate bucket, long dau, long mau) {
    }

    record RegistrationRow(LocalDate bucket, long local, long google, long other, long total) {
    }

    record DistributionRow(String label, long count) {
    }

    record TrendRow(LocalDate bucket, String label, long count) {
    }

    record DistributionResult(List<DistributionRow> distribution, List<TrendRow> trend) {
    }

    record LevelRow(int level, long count) {
    }

    record XpDistributionResult(List<DistributionRow> buckets, List<LevelRow> levels) {
    }
}
