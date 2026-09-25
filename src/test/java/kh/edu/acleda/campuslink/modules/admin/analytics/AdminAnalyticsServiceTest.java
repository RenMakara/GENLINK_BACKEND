package kh.edu.acleda.campuslink.modules.admin.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import kh.edu.acleda.campuslink.modules.admin.analytics.dto.request.AdminAnalyticsQuery;
import kh.edu.acleda.campuslink.modules.admin.analytics.dto.response.AdminAnalyticsResponse;
import kh.edu.acleda.campuslink.modules.admin.analytics.repository.AdminAnalyticsQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsServiceTest {

    @Mock
    private AdminAnalyticsQueryRepository queryRepository;

    private AdminAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AdminAnalyticsService(queryRepository);
    }

    @Test
    void mapsGrowthAndKeepsTheRequestedQueryMetadata() {
        AdminAnalyticsQuery query = query();
        when(queryRepository.findUserGrowth(query)).thenReturn(List.of(
            new AdminAnalyticsQueryRepository.SeriesRow(LocalDate.of(2026, 9, 1), 10),
            new AdminAnalyticsQueryRepository.SeriesRow(LocalDate.of(2026, 9, 2), 12)));

        AdminAnalyticsResponse.UserGrowth response = service.userGrowth(query);

        assertThat(response.from()).isEqualTo(query.from());
        assertThat(response.to()).isEqualTo(query.to());
        assertThat(response.granularity()).isEqualTo("day");
        assertThat(response.points()).extracting(AdminAnalyticsResponse.SeriesPoint::count)
            .containsExactly(10L, 12L);
        verify(queryRepository).findUserGrowth(query);
    }

    @Test
    void marksDauAndMauAsEstimatedAndKeepsFixedDistributionBuckets() {
        AdminAnalyticsQuery query = query();
        when(queryRepository.findActiveUsers(query)).thenReturn(List.of(
            new AdminAnalyticsQueryRepository.ActiveUsersRow(
                LocalDate.of(2026, 9, 1), 2, 5)));
        when(queryRepository.findPlatforms(query)).thenReturn(List.of(
            new AdminAnalyticsQueryRepository.DistributionRow("Android", 4)));

        AdminAnalyticsResponse.ActiveUsers active = service.activeUsers(query);
        AdminAnalyticsResponse.Distribution platforms = service.platforms(query);

        assertThat(active.estimated()).isTrue();
        assertThat(active.points().get(0).dau()).isEqualTo(2L);
        assertThat(platforms.distribution()).extracting(AdminAnalyticsResponse.DistributionPoint::label)
            .containsExactly("Android", "iOS", "Web", "Unknown");
        assertThat(platforms.distribution()).extracting(AdminAnalyticsResponse.DistributionPoint::count)
            .containsExactly(4L, 0L, 0L, 0L);
    }

    @Test
    void limitsUniversitiesToTwentyAndAddsAnOtherBucket() {
        AdminAnalyticsQuery query = query();
        List<AdminAnalyticsQueryRepository.DistributionRow> rows = java.util.stream.IntStream.rangeClosed(1, 21)
            .mapToObj(index -> new AdminAnalyticsQueryRepository.DistributionRow("University " + index, index))
            .toList();
        when(queryRepository.findUniversities(query)).thenReturn(rows);

        AdminAnalyticsResponse.Distribution response = service.universities(query);

        assertThat(response.distribution()).hasSize(21);
        assertThat(response.distribution().get(19).label()).isEqualTo("University 20");
        assertThat(response.distribution().get(20)).isEqualTo(
            new AdminAnalyticsResponse.DistributionPoint("Other", 21L));
    }

    private static AdminAnalyticsQuery query() {
        return AdminAnalyticsQuery.fromRequest(
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), "day");
    }
}

