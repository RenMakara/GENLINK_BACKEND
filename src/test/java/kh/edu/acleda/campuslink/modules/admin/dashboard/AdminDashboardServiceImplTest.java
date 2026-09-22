package kh.edu.acleda.campuslink.modules.admin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.analytics.repository.AdminDashboardQueryRepository;
import kh.edu.acleda.campuslink.modules.admin.dashboard.dto.response.AdminDashboardSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 23);
    private static final ZoneId PHNOM_PENH = ZoneId.of("Asia/Phnom_Penh");

    @Mock
    private AdminDashboardQueryRepository queryRepository;

    private AdminDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardServiceImpl(
            queryRepository,
            Clock.fixed(Instant.parse("2026-09-22T18:00:00Z"), PHNOM_PENH));
    }

    @Test
    void assemblesTheSevenKpisAndAllDashboardSections() {
        stubHappyPath();

        AdminDashboardSummaryResponse response = service.getSummary();

        assertThat(response.summary()).isNotNull();
        assertThat(response.summary().totalUsers()).isEqualTo(100L);
        assertThat(response.summary().totalUsersDelta()).isEqualTo(11.1);
        assertThat(response.summary().dau()).isEqualTo(8L);
        assertThat(response.summary().dauEstimated()).isTrue();
        assertThat(response.summary().mauEstimated()).isTrue();
        assertThat(response.userGrowth()).extracting(AdminDashboardSummaryResponse.UserGrowthPoint::date)
            .containsExactly(TODAY.minusDays(89));
        assertThat(response.registrations()).hasSize(1);
        assertThat(response.activityTrends()).hasSize(1);
        assertThat(response.statusSplit()).extracting(AdminDashboardSummaryResponse.StatusSplitPoint::status)
            .containsExactly("PENDING", "ACTIVE", "SUSPENDED", "BANNED", "DELETED");
        assertThat(response.recentAdminActions()).hasSize(1);
        assertThat(response.recentRegistrations()).hasSize(1);
    }

    @Test
    void aBrokenChartSectionReturnsNullWithoutBlankingTheOtherSections() {
        when(queryRepository.findKpiCounts(TODAY)).thenReturn(
            new AdminDashboardQueryRepository.KpiCounts(1, 1, 1, 1, 0, 0, 1, 0));
        when(queryRepository.findActivitySnapshot(TODAY)).thenReturn(
            new AdminDashboardQueryRepository.ActivitySnapshot(0, 0, 0, 0));
        when(queryRepository.findUserGrowth(TODAY.minusDays(89), TODAY.plusDays(1)))
            .thenReturn(List.of(new AdminDashboardQueryRepository.UserGrowthRow(TODAY, 1)));
        when(queryRepository.findActivityTrends(TODAY.minusDays(29), TODAY))
            .thenThrow(new IllegalStateException("aggregate unavailable"));
        when(queryRepository.findStatusSplit()).thenReturn(List.of());
        when(queryRepository.findRecentAdminActions(anyInt())).thenReturn(List.of());
        when(queryRepository.findRecentRegistrations(anyInt())).thenReturn(List.of());

        AdminDashboardSummaryResponse response = service.getSummary();

        assertThat(response.summary()).isNotNull();
        assertThat(response.userGrowth()).isNotNull();
        assertThat(response.activityTrends()).isNull();
        assertThat(response.statusSplit()).isNotNull();
        assertThat(response.recentAdminActions()).isNotNull();
    }

    @Test
    void missingStatusesAreReturnedAsZeroSoTheChartShapeIsStable() {
        when(queryRepository.findKpiCounts(TODAY)).thenReturn(
            new AdminDashboardQueryRepository.KpiCounts(0, 0, 0, 0, 0, 0, 0, 0));
        when(queryRepository.findActivitySnapshot(TODAY)).thenReturn(
            new AdminDashboardQueryRepository.ActivitySnapshot(0, 0, 0, 0));
        when(queryRepository.findStatusSplit()).thenReturn(
            List.of(new AdminDashboardQueryRepository.StatusCountRow("ACTIVE", 4)));

        AdminDashboardSummaryResponse response = service.getSummary();

        assertThat(response.statusSplit())
            .extracting(AdminDashboardSummaryResponse.StatusSplitPoint::count)
            .containsExactly(0L, 4L, 0L, 0L, 0L);
    }

    private void stubHappyPath() {
        when(queryRepository.findKpiCounts(TODAY)).thenReturn(
            new AdminDashboardQueryRepository.KpiCounts(100, 90, 80, 90, 10, 9, 5, 3));
        when(queryRepository.findActivitySnapshot(TODAY)).thenReturn(
            new AdminDashboardQueryRepository.ActivitySnapshot(8, 4, 40, 20));
        when(queryRepository.findUserGrowth(TODAY.minusDays(89), TODAY.plusDays(1)))
            .thenReturn(List.of(new AdminDashboardQueryRepository.UserGrowthRow(TODAY.minusDays(89), 100)));
        when(queryRepository.findRegistrations(TODAY.minusDays(29), TODAY.plusDays(1)))
            .thenReturn(List.of(new AdminDashboardQueryRepository.DailyCountRow(TODAY, 10)));
        when(queryRepository.findActivityTrends(TODAY.minusDays(29), TODAY))
            .thenReturn(List.of(new AdminDashboardQueryRepository.ActivityTrendRow(TODAY, 8, 40)));
        when(queryRepository.findStatusSplit()).thenReturn(List.of(
            new AdminDashboardQueryRepository.StatusCountRow("ACTIVE", 80),
            new AdminDashboardQueryRepository.StatusCountRow("PENDING", 5)));
        when(queryRepository.findRecentAdminActions(10)).thenReturn(List.of(
            new AdminDashboardQueryRepository.RecentAdminActionRow(
                UUID.randomUUID(), "admin@genlink.kh", "USER_SUSPEND", "user@genlink.kh",
                Instant.parse("2026-09-23T01:00:00Z"))));
        when(queryRepository.findRecentRegistrations(10)).thenReturn(List.of(
            new AdminDashboardQueryRepository.RecentRegistrationRow(
                UUID.randomUUID(), null, "New User", "new@genlink.kh", "RUPP", "PENDING",
                Instant.parse("2026-09-23T01:00:00Z"))));
    }
}
