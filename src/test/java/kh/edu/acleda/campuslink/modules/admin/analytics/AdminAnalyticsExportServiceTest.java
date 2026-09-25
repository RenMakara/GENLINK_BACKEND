package kh.edu.acleda.campuslink.modules.admin.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.time.LocalDate;
import java.util.List;
import kh.edu.acleda.campuslink.modules.admin.analytics.dto.request.AdminAnalyticsExportRequest;
import kh.edu.acleda.campuslink.modules.admin.analytics.dto.request.AdminAnalyticsQuery;
import kh.edu.acleda.campuslink.modules.admin.analytics.dto.request.AdminAnalyticsSeries;
import kh.edu.acleda.campuslink.modules.admin.analytics.dto.response.AdminAnalyticsResponse;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditEvent;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditService;
import kh.edu.acleda.campuslink.modules.admin.audit.enums.AuditStatus;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsExportServiceTest {

    private static final String REASON = "Monthly analytics review";

    @Mock
    private AdminAnalyticsService analyticsService;

    @Mock
    private AdminAuditService auditService;

    private AdminAnalyticsExportService exportService;

    @BeforeEach
    void setUp() {
        exportService = new AdminAnalyticsExportService(analyticsService, auditService);
        lenient().when(auditService.toJson(any())).thenReturn("{}");
    }

    @Test
    void exportsLocalDatesAndEscapesDistributionLabels() {
        AdminAnalyticsQuery query = query();
        when(analyticsService.universities(query)).thenReturn(new AdminAnalyticsResponse.Distribution(
            query.from(), query.to(), query.wireGranularity(),
            List.of(new AdminAnalyticsResponse.DistributionPoint("Royal, University", 4)),
            List.of(new AdminAnalyticsResponse.TrendPoint(
                LocalDate.of(2026, 9, 1), "Royal, University", 2))));

        String csv = new String(
            exportService.export(request("universities")).clone(),
            java.nio.charset.StandardCharsets.UTF_8);

        assertThat(csv).startsWith("rowType,bucket,label,count\r\n");
        assertThat(csv).contains(
            "distribution,,\"Royal, University\",4\r\n",
            "trend,2026-09-01,\"Royal, University\",2\r\n");
        verifySuccessfulAudit("universities", 2);
    }

    @Test
    void dispatchesEverySupportedSeries() {
        AdminAnalyticsQuery query = query();
        when(analyticsService.userGrowth(query)).thenReturn(new AdminAnalyticsResponse.UserGrowth(
            query.from(), query.to(), query.wireGranularity(), List.of()));
        when(analyticsService.activeUsers(query)).thenReturn(new AdminAnalyticsResponse.ActiveUsers(
            query.from(), query.to(), query.wireGranularity(), true, List.of()));
        when(analyticsService.registrations(query)).thenReturn(new AdminAnalyticsResponse.Registrations(
            query.from(), query.to(), query.wireGranularity(), List.of()));
        when(analyticsService.userStatus(query)).thenReturn(distribution(query));
        when(analyticsService.universities(query)).thenReturn(distribution(query));
        when(analyticsService.workStatus(query)).thenReturn(distribution(query));
        when(analyticsService.xpDistribution(query)).thenReturn(new AdminAnalyticsResponse.XpDistribution(
            query.from(), query.to(), query.wireGranularity(), List.of(), List.of()));
        when(analyticsService.languages(query)).thenReturn(distribution(query));
        when(analyticsService.platforms(query)).thenReturn(distribution(query));

        for (AdminAnalyticsSeries series : AdminAnalyticsSeries.values()) {
            byte[] csv = exportService.export(request(series.wireValue()));
            assertThat(csv).isNotEmpty();
        }

        verify(auditService, org.mockito.Mockito.times(AdminAnalyticsSeries.values().length))
            .record(any(AdminAuditEvent.class));
    }

    @Test
    void auditsAndPropagatesAnalyticsFailuresWithoutExposingDetails() {
        when(analyticsService.userGrowth(any()))
            .thenThrow(new ApiException("query failed internally", "QUERY_FAILED"));

        assertThatThrownBy(() -> exportService.export(request("user-growth")))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", "QUERY_FAILED");

        ArgumentCaptor<AdminAuditEvent> audit = ArgumentCaptor.forClass(AdminAuditEvent.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("ANALYTICS_EXPORT");
        assertThat(audit.getValue().status()).isEqualTo(AuditStatus.FAILURE);
        assertThat(audit.getValue().errorCode()).isEqualTo("QUERY_FAILED");
    }

    @Test
    void requiresAReasonBeforeQueryingAnalytics() {
        AdminAnalyticsExportRequest request = new AdminAnalyticsExportRequest(
            "user-growth", null, null, null, "short");

        assertThatThrownBy(() -> exportService.export(request))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", "VALIDATION_ERROR");

        verifyNoInteractions(analyticsService);
        verify(auditService).recordFailure(
            org.mockito.ArgumentMatchers.eq("ANALYTICS_EXPORT"),
            org.mockito.ArgumentMatchers.eq("ANALYTICS"),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.eq("short"),
            org.mockito.ArgumentMatchers.eq("VALIDATION_ERROR"));
    }

    private void verifySuccessfulAudit(String series, int rowCount) {
        ArgumentCaptor<AdminAuditEvent> audit = ArgumentCaptor.forClass(AdminAuditEvent.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("ANALYTICS_EXPORT");
        assertThat(audit.getValue().targetType()).isEqualTo("ANALYTICS");
        assertThat(audit.getValue().reason()).isEqualTo(REASON);
        assertThat(audit.getValue().status()).isEqualTo(AuditStatus.SUCCESS);
        assertThat(audit.getValue().afterJson()).isEqualTo("{}");
        assertThat(series).isEqualTo("universities");
        assertThat(rowCount).isEqualTo(2);
    }

    private static AdminAnalyticsResponse.Distribution distribution(AdminAnalyticsQuery query) {
        return new AdminAnalyticsResponse.Distribution(
            query.from(), query.to(), query.wireGranularity(), List.of(), List.of());
    }

    private static AdminAnalyticsExportRequest request(String series) {
        return new AdminAnalyticsExportRequest(
            series,
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 2),
            "day",
            REASON);
    }

    private static AdminAnalyticsQuery query() {
        return AdminAnalyticsQuery.fromRequest(
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), "day");
    }
}
