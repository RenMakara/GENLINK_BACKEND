package kh.edu.acleda.campuslink.modules.admin.analytics.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only aggregate boundary for the admin dashboard.
 *
 * <p>The admin module normally delegates to domain services. Analytics is the documented
 * exception: the dashboard needs grouped counts across users, authentication and admin-audit
 * tables, and returning entities here would both create N+1 queries and leak domain objects
 * across the module boundary.
 */
public interface AdminDashboardQueryRepository {

    KpiCounts findKpiCounts(LocalDate today);

    ActivitySnapshot findActivitySnapshot(LocalDate today);

    List<UserGrowthRow> findUserGrowth(LocalDate from, LocalDate toExclusive);

    List<DailyCountRow> findRegistrations(LocalDate from, LocalDate toExclusive);

    List<ActivityTrendRow> findActivityTrends(LocalDate from, LocalDate toInclusive);

    List<StatusCountRow> findStatusSplit();

    List<RecentAdminActionRow> findRecentAdminActions(int limit);

    List<RecentRegistrationRow> findRecentRegistrations(int limit);

    record KpiCounts(
        long totalUsers,
        long totalUsersBeforeToday,
        long activeUsers,
        long activeUsersBeforeToday,
        long newToday,
        long newYesterday,
        long pendingVerification,
        long bannedOrSuspended
    ) {
    }

    record ActivitySnapshot(
        long dau,
        long dauPreviousDay,
        long mau,
        long mauPreviousDay
    ) {
    }

    record UserGrowthRow(LocalDate date, long count) {
    }

    record DailyCountRow(LocalDate date, long count) {
    }

    record ActivityTrendRow(LocalDate date, long dau, long mau) {
    }

    record StatusCountRow(String status, long count) {
    }

    record RecentAdminActionRow(
        UUID id,
        String actorEmail,
        String action,
        String target,
        Instant timestamp
    ) {
    }

    record RecentRegistrationRow(
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
