package kh.edu.acleda.campuslink.modules.admin.dashboard;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;
import kh.edu.acleda.campuslink.modules.admin.analytics.AnalyticsTimeZone;
import kh.edu.acleda.campuslink.modules.admin.analytics.repository.AdminDashboardQueryRepository;
import kh.edu.acleda.campuslink.modules.admin.dashboard.dto.response.AdminDashboardSummaryResponse;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Assembles the dashboard response while keeping each read independently optional.
 *
 * <p>The query repository deliberately owns its own read-only transaction. This service does
 * not wrap all sections in one transaction: if a database aggregate fails, the failing query
 * must not poison the transaction needed by the remaining cards and panels.
 */
@Slf4j
@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final int RECENT_LIMIT = 10;
    private static final int USER_GROWTH_DAYS = 90;
    private static final int REGISTRATION_DAYS = 30;
    private static final int ACTIVITY_DAYS = 30;

    private final AdminDashboardQueryRepository queryRepository;
    private final Clock clock;

    @Autowired
    public AdminDashboardServiceImpl(AdminDashboardQueryRepository queryRepository) {
        this(queryRepository, Clock.system(AnalyticsTimeZone.ZONE));
    }

    /** Package-private clock seam keeps the production timezone fixed and tests deterministic. */
    AdminDashboardServiceImpl(AdminDashboardQueryRepository queryRepository, Clock clock) {
        this.queryRepository = queryRepository;
        this.clock = clock;
    }

    @Override
    public AdminDashboardSummaryResponse getSummary() {
        LocalDate today = LocalDate.now(clock);

        return new AdminDashboardSummaryResponse(
            safeSection("summary", () -> summary(today)),
            safeSection("userGrowth", () -> userGrowth(today)),
            safeSection("registrations", () -> registrations(today)),
            safeSection("activityTrends", () -> activityTrends(today)),
            safeSection("statusSplit", this::statusSplit),
            safeSection("recentAdminActions", this::recentAdminActions),
            safeSection("recentRegistrations", this::recentRegistrations)
        );
    }

    private AdminDashboardSummaryResponse.KpiSummary summary(LocalDate today) {
        AdminDashboardQueryRepository.KpiCounts counts = queryRepository.findKpiCounts(today);
        AdminDashboardQueryRepository.ActivitySnapshot activity =
            queryRepository.findActivitySnapshot(today);

        return new AdminDashboardSummaryResponse.KpiSummary(
            counts.totalUsers(),
            percentageDelta(counts.totalUsers(), counts.totalUsersBeforeToday()),
            counts.activeUsers(),
            percentageDelta(counts.activeUsers(), counts.activeUsersBeforeToday()),
            counts.newToday(),
            percentageDelta(counts.newToday(), counts.newYesterday()),
            activity.dau(),
            percentageDelta(activity.dau(), activity.dauPreviousDay()),
            true,
            activity.mau(),
            percentageDelta(activity.mau(), activity.mauPreviousDay()),
            true,
            counts.pendingVerification(),
            counts.bannedOrSuspended()
        );
    }

    private List<AdminDashboardSummaryResponse.UserGrowthPoint> userGrowth(LocalDate today) {
        LocalDate from = today.minusDays(USER_GROWTH_DAYS - 1L);
        return queryRepository.findUserGrowth(from, today.plusDays(1L)).stream()
            .map(row -> new AdminDashboardSummaryResponse.UserGrowthPoint(row.date(), row.count()))
            .toList();
    }

    private List<AdminDashboardSummaryResponse.RegistrationPoint> registrations(LocalDate today) {
        LocalDate from = today.minusDays(REGISTRATION_DAYS - 1L);
        return queryRepository.findRegistrations(from, today.plusDays(1L)).stream()
            .map(row -> new AdminDashboardSummaryResponse.RegistrationPoint(row.date(), row.count()))
            .toList();
    }

    private List<AdminDashboardSummaryResponse.ActivityPoint> activityTrends(LocalDate today) {
        LocalDate from = today.minusDays(ACTIVITY_DAYS - 1L);
        return queryRepository.findActivityTrends(from, today).stream()
            .map(row -> new AdminDashboardSummaryResponse.ActivityPoint(row.date(), row.dau(), row.mau()))
            .toList();
    }

    private List<AdminDashboardSummaryResponse.StatusSplitPoint> statusSplit() {
        EnumMap<AccountStatus, Long> counts = new EnumMap<>(AccountStatus.class);
        for (AdminDashboardQueryRepository.StatusCountRow row : queryRepository.findStatusSplit()) {
            try {
                counts.put(AccountStatus.valueOf(row.status()), row.count());
            } catch (IllegalArgumentException ex) {
                // A future database status should not make the whole dashboard disappear.
                log.warn("Ignoring unknown account status in dashboard aggregate: {}", row.status());
            }
        }

        // Keep zero-valued statuses in the response so the donut legend remains stable across
        // deployments and an empty dev database still has the five SSOT categories.
        return List.of(
            AccountStatus.PENDING,
            AccountStatus.ACTIVE,
            AccountStatus.SUSPENDED,
            AccountStatus.BANNED,
            AccountStatus.DELETED
        ).stream()
            .map(status -> new AdminDashboardSummaryResponse.StatusSplitPoint(
                status.name(), counts.getOrDefault(status, 0L)))
            .toList();
    }

    private List<AdminDashboardSummaryResponse.AdminAction> recentAdminActions() {
        return queryRepository.findRecentAdminActions(RECENT_LIMIT).stream()
            .map(row -> new AdminDashboardSummaryResponse.AdminAction(
                row.id(), row.actorEmail(), row.action(), row.target(), row.timestamp()))
            .toList();
    }

    private List<AdminDashboardSummaryResponse.RecentRegistration> recentRegistrations() {
        return queryRepository.findRecentRegistrations(RECENT_LIMIT).stream()
            .map(row -> new AdminDashboardSummaryResponse.RecentRegistration(
                row.id(), row.avatarUrl(), row.fullName(), row.email(), row.universityName(),
                row.status(), row.joinedAt()))
            .toList();
    }

    private <T> T safeSection(String section, Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException ex) {
            // The endpoint is intentionally a partial-success response. Keep the exception out
            // of the HTTP layer, but retain enough context to diagnose the failed aggregate.
            log.warn("Admin dashboard section '{}' unavailable: {}", section, ex.getMessage());
            return null;
        }
    }

    private static Double percentageDelta(long current, long previous) {
        if (previous == 0) {
            return current == 0 ? 0.0 : null;
        }
        double percentage = ((double) current - previous) * 100.0 / previous;
        return Math.round(percentage * 10.0) / 10.0;
    }
}
