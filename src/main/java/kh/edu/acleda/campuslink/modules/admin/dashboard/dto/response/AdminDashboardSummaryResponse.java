package kh.edu.acleda.campuslink.modules.admin.dashboard.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The read-only payload for the admin overview page.
 *
 * <p>Each top-level member is nullable on purpose. The dashboard is a BFF-lite endpoint: a
 * broken aggregate should make only its card, chart or panel render an empty state, rather than
 * turning the whole overview into an error page.
 */
public record AdminDashboardSummaryResponse(
    KpiSummary summary,
    List<UserGrowthPoint> userGrowth,
    List<RegistrationPoint> registrations,
    List<ActivityPoint> activityTrends,
    List<StatusSplitPoint> statusSplit,
    List<AdminAction> recentAdminActions,
    List<RecentRegistration> recentRegistrations
) {

    /** Seven Phase 2 KPI cards. Open reports is intentionally deferred to Phase 7. */
    public record KpiSummary(
        Long totalUsers,
        Double totalUsersDelta,
        Long activeUsers,
        Double activeUsersDelta,
        Long newToday,
        Double newTodayDelta,
        Long dau,
        Double dauDelta,
        Boolean dauEstimated,
        Long mau,
        Double mauDelta,
        Boolean mauEstimated,
        Long pendingVerification,
        Long bannedOrSuspended
    ) {
    }

    public record UserGrowthPoint(LocalDate date, long count) {
    }

    public record RegistrationPoint(LocalDate date, long count) {
    }

    public record ActivityPoint(LocalDate date, long dau, long mau) {
    }

    public record StatusSplitPoint(String status, long count) {
    }

    public record AdminAction(
        UUID id,
        String actorEmail,
        String action,
        String target,
        Instant timestamp
    ) {
    }

    public record RecentRegistration(
        UUID id,
        String avatarUrl,
        String fullName,
        String email,
        String universityName,
        String status,
        Instant joinedAt
    ) {
    }
}
