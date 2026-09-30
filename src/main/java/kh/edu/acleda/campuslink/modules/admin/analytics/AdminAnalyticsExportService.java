package kh.edu.acleda.campuslink.modules.admin.analytics;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import kh.edu.acleda.campuslink.modules.admin.analytics.dto.request.AdminAnalyticsExportRequest;
import kh.edu.acleda.campuslink.modules.admin.analytics.dto.request.AdminAnalyticsQuery;
import kh.edu.acleda.campuslink.modules.admin.analytics.dto.request.AdminAnalyticsSeries;
import kh.edu.acleda.campuslink.modules.admin.analytics.dto.response.AdminAnalyticsResponse;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditEvent;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditService;
import kh.edu.acleda.campuslink.modules.admin.audit.enums.AuditStatus;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the bounded, audited synchronous analytics CSV export (ADM-115). */
@Service
public class AdminAnalyticsExportService {

    static final String ACTION_EXPORT = "ANALYTICS_EXPORT";
    static final String TARGET_TYPE_EXPORT = "ANALYTICS";
    private static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    private final AdminAnalyticsService analyticsService;
    private final AdminAuditService auditService;

    public AdminAnalyticsExportService(
        AdminAnalyticsService analyticsService,
        AdminAuditService auditService) {
        this.analyticsService = analyticsService;
        this.auditService = auditService;
    }

    /** Resolves one cached analytics response, renders it in memory. */
    @Transactional
    public byte[] export(AdminAnalyticsExportRequest request) {
        ExportSpec spec;
        try {
            spec = specification(request);
        } catch (ApiException exception) {
            recordFailure(request, null, exception.getErrorCode());
            throw exception;
        }

        try {
            CsvResult result = render(spec.series(), spec.query());
            record(spec, result.rowCount(), AuditStatus.SUCCESS, null);
            return result.csv().getBytes(StandardCharsets.UTF_8);
        } catch (ApiException exception) {
            record(spec, 0, AuditStatus.FAILURE, exception.getErrorCode());
            throw exception;
        } catch (RuntimeException exception) {
            record(spec, 0, AuditStatus.FAILURE, INTERNAL_SERVER_ERROR);
            throw exception;
        }
    }

    private ExportSpec specification(AdminAnalyticsExportRequest request) {
        if (request == null) {
            throw new ApiException("Request body is required", "VALIDATION_ERROR");
        }
        String reason = requireReason(request.reason());
        return new ExportSpec(
            AdminAnalyticsSeries.fromValue(request.series()),
            AdminAnalyticsQuery.fromRequest(request.from(), request.to(), request.granularity()),
            reason);
    }

    private CsvResult render(AdminAnalyticsSeries series, AdminAnalyticsQuery query) {
        return switch (series) {
            case USER_GROWTH -> renderUserGrowth(analyticsService.userGrowth(query));
            case ACTIVE_USERS -> renderActiveUsers(analyticsService.activeUsers(query));
            case REGISTRATIONS -> renderRegistrations(analyticsService.registrations(query));
            case USER_STATUS -> renderDistribution(analyticsService.userStatus(query));
            case UNIVERSITIES -> renderDistribution(analyticsService.universities(query));
            case WORK_STATUS -> renderDistribution(analyticsService.workStatus(query));
            case XP_DISTRIBUTION -> renderXpDistribution(analyticsService.xpDistribution(query));
            case LANGUAGES -> renderDistribution(analyticsService.languages(query));
            case PLATFORMS -> renderDistribution(analyticsService.platforms(query));
        };
    }

    private static CsvResult renderUserGrowth(AdminAnalyticsResponse.UserGrowth response) {
        StringBuilder csv = new StringBuilder();
        row(csv, "bucket", "count");
        for (AdminAnalyticsResponse.SeriesPoint point : response.points()) {
            row(csv, point.bucket(), point.count());
        }
        return new CsvResult(csv.toString(), response.points().size());
    }

    private static CsvResult renderActiveUsers(AdminAnalyticsResponse.ActiveUsers response) {
        StringBuilder csv = new StringBuilder();
        row(csv, "bucket", "dau", "mau", "estimated");
        for (AdminAnalyticsResponse.ActiveUsersPoint point : response.points()) {
            row(csv, point.bucket(), point.dau(), point.mau(), response.estimated());
        }
        return new CsvResult(csv.toString(), response.points().size());
    }

    private static CsvResult renderRegistrations(AdminAnalyticsResponse.Registrations response) {
        StringBuilder csv = new StringBuilder();
        row(csv, "bucket", "local", "google", "other", "total");
        for (AdminAnalyticsResponse.RegistrationPoint point : response.points()) {
            row(csv, point.bucket(), point.local(), point.google(), point.other(), point.total());
        }
        return new CsvResult(csv.toString(), response.points().size());
    }

    private static CsvResult renderDistribution(AdminAnalyticsResponse.Distribution response) {
        StringBuilder csv = new StringBuilder();
        row(csv, "rowType", "bucket", "label", "count");
        int rowCount = 0;
        for (AdminAnalyticsResponse.DistributionPoint point : response.distribution()) {
            row(csv, "distribution", "", point.label(), point.count());
            rowCount++;
        }
        for (AdminAnalyticsResponse.TrendPoint point : response.trend()) {
            row(csv, "trend", point.bucket(), point.label(), point.count());
            rowCount++;
        }
        return new CsvResult(csv.toString(), rowCount);
    }

    private static CsvResult renderXpDistribution(AdminAnalyticsResponse.XpDistribution response) {
        StringBuilder csv = new StringBuilder();
        row(csv, "rowType", "label", "count", "level");
        int rowCount = 0;
        for (AdminAnalyticsResponse.DistributionPoint point : response.buckets()) {
            row(csv, "bucket", point.label(), point.count(), "");
            rowCount++;
        }
        for (AdminAnalyticsResponse.LevelPoint point : response.levels()) {
            row(csv, "level", "", point.count(), point.level());
            rowCount++;
        }
        return new CsvResult(csv.toString(), rowCount);
    }

    private void record(
        ExportSpec spec,
        long rowCount,
        AuditStatus status,
        String errorCode) {
        auditService.record(AdminAuditEvent.builder()
            .action(ACTION_EXPORT)
            .targetType(TARGET_TYPE_EXPORT)
            .reason(spec.reason())
            .afterJson(auditService.toJson(new ExportAuditSnapshot(
                spec.series().wireValue(),
                spec.query().from(),
                spec.query().to(),
                spec.query().wireGranularity(),
                rowCount)))
            .status(status)
            .errorCode(errorCode)
            .build());
    }

    private void recordFailure(
        AdminAnalyticsExportRequest request,
        String reason,
        String errorCode) {
        String requestReason = reason == null ? safeReason(request) : reason;
        auditService.recordFailure(
            ACTION_EXPORT, TARGET_TYPE_EXPORT, null, requestReason, errorCode);
    }

    private static String requireReason(String reason) {
        if (reason == null) {
            throw new ApiException("reason is required", "VALIDATION_ERROR");
        }
        String trimmed = reason.trim();
        if (trimmed.length() < 10 || trimmed.length() > 2_000) {
            throw new ApiException(
                "reason must contain between 10 and 2000 characters", "VALIDATION_ERROR");
        }
        return trimmed;
    }

    private static String safeReason(AdminAnalyticsExportRequest request) {
        if (request == null || request.reason() == null) {
            return null;
        }
        String trimmed = request.reason().trim();
        return trimmed.length() > 2_000 ? trimmed.substring(0, 2_000) : trimmed;
    }

    private static void row(StringBuilder csv, Object... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                csv.append(',');
            }
            writeValue(csv, values[index]);
        }
        csv.append("\r\n");
    }

    private static void writeValue(StringBuilder csv, Object value) {
        String safe = value == null ? "" : String.valueOf(value);
        boolean quoted = safe.indexOf(',') >= 0
            || safe.indexOf('"') >= 0
            || safe.indexOf('\r') >= 0
            || safe.indexOf('\n') >= 0;
        if (quoted) {
            csv.append('"').append(safe.replace("\"", "\"\""));
            csv.append('"');
        } else {
            csv.append(safe);
        }
    }

    private record ExportSpec(
        AdminAnalyticsSeries series,
        AdminAnalyticsQuery query,
        String reason
    ) {
    }

    private record CsvResult(String csv, int rowCount) {
    }

    private record ExportAuditSnapshot(
        String series,
        LocalDate from,
        LocalDate to,
        String granularity,
        long rowCount
    ) {
    }
}
