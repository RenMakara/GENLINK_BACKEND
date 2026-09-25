package kh.edu.acleda.campuslink.modules.admin.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import kh.edu.acleda.campuslink.modules.admin.analytics.cache.AnalyticsCacheConfig;
import kh.edu.acleda.campuslink.modules.admin.analytics.cache.AnalyticsCacheManager;
import kh.edu.acleda.campuslink.modules.admin.analytics.dto.request.AdminAnalyticsQuery;
import kh.edu.acleda.campuslink.modules.admin.analytics.dto.response.AdminAnalyticsResponse;
import kh.edu.acleda.campuslink.modules.admin.analytics.repository.AdminAnalyticsQueryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import kh.edu.acleda.campuslink.shared.config.CacheConfig;

class AdminAnalyticsServiceCacheTest {

    @Test
    void cachesIdenticalRequestsAndKeepsEndpointKeysSeparate() {
        AdminAnalyticsQueryRepository repository = mock(AdminAnalyticsQueryRepository.class);
        AdminAnalyticsQuery query = AdminAnalyticsQuery.fromRequest(
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), "day");
        AdminAnalyticsResponse.UserGrowth growth = new AdminAnalyticsResponse.UserGrowth(
            query.from(), query.to(), query.wireGranularity(),
            List.of(new AdminAnalyticsResponse.SeriesPoint(LocalDate.of(2026, 9, 1), 3)));
        AdminAnalyticsResponse.Distribution platforms = new AdminAnalyticsResponse.Distribution(
            query.from(), query.to(), query.wireGranularity(), List.of(
                new AdminAnalyticsResponse.DistributionPoint("Android", 0),
                new AdminAnalyticsResponse.DistributionPoint("iOS", 0),
                new AdminAnalyticsResponse.DistributionPoint("Web", 0),
                new AdminAnalyticsResponse.DistributionPoint("Unknown", 0)), List.of());
        when(repository.findUserGrowth(query)).thenReturn(List.of(
            new AdminAnalyticsQueryRepository.SeriesRow(LocalDate.of(2026, 9, 1), 3)));
        when(repository.findPlatforms(query)).thenReturn(List.of());

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(AdminAnalyticsQueryRepository.class, () -> repository);
            context.registerBean(AdminAnalyticsService.class);
            context.registerBean(
                AnalyticsCacheConfig.CACHE_MANAGER_BEAN,
                CacheManager.class,
                () -> new AnalyticsCacheManager(
                    AnalyticsCacheConfig.CACHE_NAME,
                    AnalyticsCacheConfig.TTL,
                    Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));
            context.register(CacheConfig.class, CacheTestConfiguration.class);
            context.refresh();

            AdminAnalyticsService service = context.getBean(AdminAnalyticsService.class);

            assertThat(service.userGrowth(query)).isEqualTo(growth);
            assertThat(service.userGrowth(query)).isEqualTo(growth);
            assertThat(service.platforms(query)).isEqualTo(platforms);

            verify(repository).findUserGrowth(query);
            verify(repository).findPlatforms(query);
            verifyNoMoreInteractions(repository);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class CacheTestConfiguration {
    }
}
