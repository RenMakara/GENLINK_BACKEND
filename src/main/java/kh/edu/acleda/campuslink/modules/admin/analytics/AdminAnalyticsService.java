package kh.edu.acleda.campuslink.modules.admin.analytics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kh.edu.acleda.campuslink.modules.admin.analytics.cache.AnalyticsCacheConfig;
import kh.edu.acleda.campuslink.modules.admin.analytics.dto.request.AdminAnalyticsQuery;
import kh.edu.acleda.campuslink.modules.admin.analytics.dto.response.AdminAnalyticsResponse;
import kh.edu.acleda.campuslink.modules.admin.analytics.repository.AdminAnalyticsQueryRepository;
import kh.edu.acleda.campuslink.modules.admin.analytics.repository.AdminAnalyticsQueryRepository.DistributionResult;
import kh.edu.acleda.campuslink.modules.admin.analytics.repository.AdminAnalyticsQueryRepository.DistributionRow;
import kh.edu.acleda.campuslink.modules.admin.analytics.repository.AdminAnalyticsQueryRepository.TrendRow;
import org.springframework.cache.annotation.Cacheable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maps read-only analytics projections to the public admin API contract. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAnalyticsService {

    private static final List<String> ACCOUNT_STATUSES = List.of(
        "PENDING", "ACTIVE", "SUSPENDED", "BANNED", "DELETED");
    private static final List<String> WORK_STATUSES = List.of(
        "STUDENT", "FRESH_GRADUATE", "YOUNG_PROFESSIONAL", "CREATOR",
        "FREELANCER", "ENTREPRENEUR", "OTHER");
    private static final List<String> LANGUAGES = List.of("ENGLISH", "KHMER");
    private static final List<String> PLATFORMS = List.of("Android", "iOS", "Web", "Unknown");
    private static final List<String> XP_BUCKETS = List.of(
        "0-99", "100-499", "500-999", "1k-4.9k", "5k+");

    private final AdminAnalyticsQueryRepository queryRepository;

    @Cacheable(
        cacheNames = AnalyticsCacheConfig.CACHE_NAME,
        cacheManager = AnalyticsCacheConfig.CACHE_MANAGER_BEAN,
        key = "'user-growth:' + #root.args[0]",
        sync = true)
    public AdminAnalyticsResponse.UserGrowth userGrowth(AdminAnalyticsQuery query) {
        List<AdminAnalyticsResponse.SeriesPoint> points = queryRepository.findUserGrowth(query).stream()
            .map(row -> new AdminAnalyticsResponse.SeriesPoint(row.bucket(), row.count()))
            .toList();
        return new AdminAnalyticsResponse.UserGrowth(
            query.from(), query.to(), query.wireGranularity(), points);
    }

    @Cacheable(
        cacheNames = AnalyticsCacheConfig.CACHE_NAME,
        cacheManager = AnalyticsCacheConfig.CACHE_MANAGER_BEAN,
        key = "'active-users:' + #root.args[0]",
        sync = true)
    public AdminAnalyticsResponse.ActiveUsers activeUsers(AdminAnalyticsQuery query) {
        List<AdminAnalyticsResponse.ActiveUsersPoint> points = queryRepository.findActiveUsers(query).stream()
            .map(row -> new AdminAnalyticsResponse.ActiveUsersPoint(
                row.bucket(), row.dau(), row.mau()))
            .toList();
        return new AdminAnalyticsResponse.ActiveUsers(
            query.from(), query.to(), query.wireGranularity(), true, points);
    }

    @Cacheable(
        cacheNames = AnalyticsCacheConfig.CACHE_NAME,
        cacheManager = AnalyticsCacheConfig.CACHE_MANAGER_BEAN,
        key = "'registrations:' + #root.args[0]",
        sync = true)
    public AdminAnalyticsResponse.Registrations registrations(AdminAnalyticsQuery query) {
        List<AdminAnalyticsResponse.RegistrationPoint> points = queryRepository
            .findRegistrations(query).stream()
            .map(row -> new AdminAnalyticsResponse.RegistrationPoint(
                row.bucket(), row.local(), row.google(), row.other(), row.total()))
            .toList();
        return new AdminAnalyticsResponse.Registrations(
            query.from(), query.to(), query.wireGranularity(), points);
    }

    @Cacheable(
        cacheNames = AnalyticsCacheConfig.CACHE_NAME,
        cacheManager = AnalyticsCacheConfig.CACHE_MANAGER_BEAN,
        key = "'user-status:' + #root.args[0]",
        sync = true)
    public AdminAnalyticsResponse.Distribution userStatus(AdminAnalyticsQuery query) {
        return distribution(query, queryRepository.findUserStatus(query), ACCOUNT_STATUSES);
    }

    @Cacheable(
        cacheNames = AnalyticsCacheConfig.CACHE_NAME,
        cacheManager = AnalyticsCacheConfig.CACHE_MANAGER_BEAN,
        key = "'universities:' + #root.args[0]",
        sync = true)
    public AdminAnalyticsResponse.Distribution universities(AdminAnalyticsQuery query) {
        List<DistributionRow> rows = queryRepository.findUniversities(query);
        List<DistributionRow> top = rows.stream().limit(20).toList();
        long other = rows.stream().skip(20).mapToLong(DistributionRow::count).sum();
        List<AdminAnalyticsResponse.DistributionPoint> distribution = new ArrayList<>(
            top.stream().map(this::distributionPoint).toList());
        distribution.add(new AdminAnalyticsResponse.DistributionPoint("Other", other));
        return new AdminAnalyticsResponse.Distribution(
            query.from(), query.to(), query.wireGranularity(), List.copyOf(distribution), List.of());
    }

    @Cacheable(
        cacheNames = AnalyticsCacheConfig.CACHE_NAME,
        cacheManager = AnalyticsCacheConfig.CACHE_MANAGER_BEAN,
        key = "'work-status:' + #root.args[0]",
        sync = true)
    public AdminAnalyticsResponse.Distribution workStatus(AdminAnalyticsQuery query) {
        return distribution(
            query,
            new DistributionResult(queryRepository.findWorkStatus(query), List.of()),
            WORK_STATUSES);
    }

    @Cacheable(
        cacheNames = AnalyticsCacheConfig.CACHE_NAME,
        cacheManager = AnalyticsCacheConfig.CACHE_MANAGER_BEAN,
        key = "'xp-distribution:' + #root.args[0]",
        sync = true)
    public AdminAnalyticsResponse.XpDistribution xpDistribution(AdminAnalyticsQuery query) {
        var result = queryRepository.findXpDistribution(query);
        return new AdminAnalyticsResponse.XpDistribution(
            query.from(),
            query.to(),
            query.wireGranularity(),
            fixedDistribution(result.buckets(), XP_BUCKETS),
            result.levels().stream()
                .map(row -> new AdminAnalyticsResponse.LevelPoint(row.level(), row.count()))
                .toList());
    }

    @Cacheable(
        cacheNames = AnalyticsCacheConfig.CACHE_NAME,
        cacheManager = AnalyticsCacheConfig.CACHE_MANAGER_BEAN,
        key = "'languages:' + #root.args[0]",
        sync = true)
    public AdminAnalyticsResponse.Distribution languages(AdminAnalyticsQuery query) {
        return distribution(query, queryRepository.findLanguages(query), LANGUAGES);
    }

    @Cacheable(
        cacheNames = AnalyticsCacheConfig.CACHE_NAME,
        cacheManager = AnalyticsCacheConfig.CACHE_MANAGER_BEAN,
        key = "'platforms:' + #root.args[0]",
        sync = true)
    public AdminAnalyticsResponse.Distribution platforms(AdminAnalyticsQuery query) {
        return new AdminAnalyticsResponse.Distribution(
            query.from(),
            query.to(),
            query.wireGranularity(),
            fixedDistribution(queryRepository.findPlatforms(query), PLATFORMS),
            List.of());
    }

    private AdminAnalyticsResponse.Distribution distribution(
        AdminAnalyticsQuery query,
        DistributionResult result,
        List<String> fixedLabels) {
        return new AdminAnalyticsResponse.Distribution(
            query.from(),
            query.to(),
            query.wireGranularity(),
            fixedDistribution(result.distribution(), fixedLabels),
            result.trend().stream().map(this::trendPoint).toList());
    }

    private List<AdminAnalyticsResponse.DistributionPoint> fixedDistribution(
        List<DistributionRow> rows,
        List<String> fixedLabels) {
        Map<String, Long> counts = new LinkedHashMap<>();
        fixedLabels.forEach(label -> counts.put(label, 0L));
        for (DistributionRow row : rows) {
            counts.merge(row.label(), row.count(), Long::sum);
        }
        return counts.entrySet().stream()
            .map(entry -> new AdminAnalyticsResponse.DistributionPoint(entry.getKey(), entry.getValue()))
            .toList();
    }

    private AdminAnalyticsResponse.DistributionPoint distributionPoint(DistributionRow row) {
        return new AdminAnalyticsResponse.DistributionPoint(row.label(), row.count());
    }

    private AdminAnalyticsResponse.TrendPoint trendPoint(TrendRow row) {
        return new AdminAnalyticsResponse.TrendPoint(row.bucket(), row.label(), row.count());
    }
}
