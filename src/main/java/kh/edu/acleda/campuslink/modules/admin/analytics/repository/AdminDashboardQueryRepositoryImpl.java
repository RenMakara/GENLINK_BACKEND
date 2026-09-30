package kh.edu.acleda.campuslink.modules.admin.analytics.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.analytics.AnalyticsTimeZone;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL aggregate implementation for {@link AdminDashboardQueryRepository}. */
@Repository
@Transactional(readOnly = true)
public class AdminDashboardQueryRepositoryImpl implements AdminDashboardQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public KpiCounts findKpiCounts(LocalDate today) {
        Instant todayStart = AnalyticsTimeZone.startOfDay(today);
        Instant todayEnd = AnalyticsTimeZone.endOfDayExclusive(today);
        Instant yesterdayStart = AnalyticsTimeZone.startOfDay(today.minusDays(1));

        Object[] row = (Object[]) entityManager.createNativeQuery("""
            SELECT
                COUNT(*) FILTER (WHERE u.account_status <> 'DELETED' AND u.deleted_at IS NULL),
                COUNT(*) FILTER (
                    WHERE u.account_status <> 'DELETED'
                      AND u.deleted_at IS NULL
                      AND u.created_at < :todayStart
                ),
                COUNT(*) FILTER (WHERE u.account_status = 'ACTIVE' AND u.deleted_at IS NULL),
                COUNT(*) FILTER (
                    WHERE u.account_status = 'ACTIVE'
                      AND u.deleted_at IS NULL
                      AND u.created_at < :todayStart
                ),
                COUNT(*) FILTER (
                    WHERE u.created_at >= :todayStart
                      AND u.created_at < :todayEnd
                      AND u.account_status <> 'DELETED'
                      AND u.deleted_at IS NULL
                ),
                COUNT(*) FILTER (
                    WHERE u.created_at >= :yesterdayStart
                      AND u.created_at < :todayStart
                      AND u.account_status <> 'DELETED'
                      AND u.deleted_at IS NULL
                ),
                COUNT(*) FILTER (WHERE u.account_status = 'PENDING' AND u.deleted_at IS NULL),
                COUNT(*) FILTER (
                    WHERE u.account_status IN ('BANNED', 'SUSPENDED')
                      AND u.deleted_at IS NULL
                )
            FROM users u
            """)
            .setParameter("todayStart", utcTimestamp(todayStart))
            .setParameter("todayEnd", utcTimestamp(todayEnd))
            .setParameter("yesterdayStart", utcTimestamp(yesterdayStart))
            .getSingleResult();

        return new KpiCounts(
            number(row[0]),
            number(row[1]),
            number(row[2]),
            number(row[3]),
            number(row[4]),
            number(row[5]),
            number(row[6]),
            number(row[7])
        );
    }

    @Override
    public ActivitySnapshot findActivitySnapshot(LocalDate today) {
        List<ActivityTrendRow> rows = findActivityTrends(today.minusDays(1), today);
        ActivityTrendRow previous = rows.stream()
            .filter(row -> row.date().equals(today.minusDays(1)))
            .findFirst()
            .orElse(new ActivityTrendRow(today.minusDays(1), 0, 0));
        ActivityTrendRow current = rows.stream()
            .filter(row -> row.date().equals(today))
            .findFirst()
            .orElse(new ActivityTrendRow(today, 0, 0));
        return new ActivitySnapshot(current.dau(), previous.dau(), current.mau(), previous.mau());
    }

    @Override
    public List<UserGrowthRow> findUserGrowth(LocalDate from, LocalDate toExclusive) {
        String localCreatedAt = AnalyticsTimeZone.toLocal("u.created_at");
        String sql = """
            WITH days AS (
                SELECT generate_series(
                    CAST(:fromDate AS date),
                    CAST(:toDateExclusive AS date) - INTERVAL '1 day',
                    INTERVAL '1 day'
                )::date AS day
            ), daily AS (
                SELECT %s::date AS day, COUNT(*) AS registrations
                FROM users u
                WHERE u.created_at >= :fromInstant
                  AND u.created_at < :toInstant
                  AND u.account_status <> 'DELETED'
                  AND u.deleted_at IS NULL
                GROUP BY 1
            ), baseline AS (
                SELECT COUNT(*) AS count
                FROM users u
                WHERE u.created_at < :fromInstant
                  AND u.account_status <> 'DELETED'
                  AND u.deleted_at IS NULL
            )
            SELECT days.day,
                   baseline.count
                       + SUM(COALESCE(daily.registrations, 0)) OVER (
                           ORDER BY days.day ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                       )
            FROM days
            CROSS JOIN baseline
            LEFT JOIN daily ON daily.day = days.day
            ORDER BY days.day
            """.formatted(localCreatedAt);

        return entityManager.createNativeQuery(sql)
            .setParameter("fromDate", Date.valueOf(from))
            .setParameter("toDateExclusive", Date.valueOf(toExclusive))
            .setParameter("fromInstant", utcTimestamp(AnalyticsTimeZone.startOfDay(from)))
            .setParameter("toInstant", utcTimestamp(AnalyticsTimeZone.startOfDay(toExclusive)))
            .getResultList()
            .stream()
            .map(result -> {
                Object[] row = (Object[]) result;
                return new UserGrowthRow(toLocalDate(row[0]), number(row[1]));
            })
            .toList();
    }

    @Override
    public List<DailyCountRow> findRegistrations(LocalDate from, LocalDate toExclusive) {
        String localCreatedAt = AnalyticsTimeZone.toLocal("u.created_at");
        String sql = """
            WITH days AS (
                SELECT generate_series(
                    CAST(:fromDate AS date),
                    CAST(:toDateExclusive AS date) - INTERVAL '1 day',
                    INTERVAL '1 day'
                )::date AS day
            ), daily AS (
                SELECT %s::date AS day, COUNT(*) AS registrations
                FROM users u
                WHERE u.created_at >= :fromInstant
                  AND u.created_at < :toInstant
                  AND u.account_status <> 'DELETED'
                  AND u.deleted_at IS NULL
                GROUP BY 1
            )
            SELECT days.day, COALESCE(daily.registrations, 0)
            FROM days
            LEFT JOIN daily ON daily.day = days.day
            ORDER BY days.day
            """.formatted(localCreatedAt);

        return entityManager.createNativeQuery(sql)
            .setParameter("fromDate", Date.valueOf(from))
            .setParameter("toDateExclusive", Date.valueOf(toExclusive))
            .setParameter("fromInstant", utcTimestamp(AnalyticsTimeZone.startOfDay(from)))
            .setParameter("toInstant", utcTimestamp(AnalyticsTimeZone.startOfDay(toExclusive)))
            .getResultList()
            .stream()
            .map(result -> {
                Object[] row = (Object[]) result;
                return new DailyCountRow(toLocalDate(row[0]), number(row[1]));
            })
            .toList();
    }

    @Override
    public List<ActivityTrendRow> findActivityTrends(LocalDate from, LocalDate toInclusive) {
        LocalDate activityFromDate = from.minusDays(29);
        Instant activityFrom = AnalyticsTimeZone.startOfDay(activityFromDate);
        Instant activityTo = AnalyticsTimeZone.endOfDayExclusive(toInclusive);
        String securityLocal = AnalyticsTimeZone.toLocal("sa.created_at");
        String refreshLocal = AnalyticsTimeZone.toLocal("rt.last_used_at");

        String sql = """
            WITH days AS (
                SELECT generate_series(
                    CAST(:fromDate AS date),
                    CAST(:toDate AS date),
                    INTERVAL '1 day'
                )::date AS day
            ), activity AS (
                SELECT sa.user_id, %s AS local_at
                FROM security_audit_logs sa
                JOIN users u ON u.user_id = sa.user_id
                WHERE sa.event_type = 'LOGIN_SUCCESS'
                  AND COALESCE(sa.success, TRUE) = TRUE
                  AND sa.user_id IS NOT NULL
                  AND sa.created_at >= :activityFrom
                  AND sa.created_at < :activityTo
                  AND u.account_status <> 'DELETED'
                  AND u.deleted_at IS NULL
                UNION ALL
                SELECT rt.user_id, %s AS local_at
                FROM refresh_tokens rt
                JOIN users u ON u.user_id = rt.user_id
                WHERE rt.last_used_at IS NOT NULL
                  AND rt.last_used_at >= :activityFrom
                  AND rt.last_used_at < :activityTo
                  AND u.account_status <> 'DELETED'
                  AND u.deleted_at IS NULL
            )
            SELECT days.day,
                   COUNT(DISTINCT activity.user_id) FILTER (
                       WHERE activity.local_at >= days.day::timestamp
                         AND activity.local_at < (days.day + INTERVAL '1 day')::timestamp
                   ),
                   COUNT(DISTINCT activity.user_id)
            FROM days
            LEFT JOIN activity ON activity.local_at >= (days.day - INTERVAL '29 days')::timestamp
                              AND activity.local_at < (days.day + INTERVAL '1 day')::timestamp
            GROUP BY days.day
            ORDER BY days.day
            """.formatted(securityLocal, refreshLocal);

        return entityManager.createNativeQuery(sql)
            .setParameter("fromDate", Date.valueOf(from))
            .setParameter("toDate", Date.valueOf(toInclusive))
            .setParameter("activityFrom", utcTimestamp(activityFrom))
            .setParameter("activityTo", utcTimestamp(activityTo))
            .getResultList()
            .stream()
            .map(result -> {
                Object[] row = (Object[]) result;
                return new ActivityTrendRow(toLocalDate(row[0]), number(row[1]), number(row[2]));
            })
            .toList();
    }

    @Override
    public List<StatusCountRow> findStatusSplit() {
        return entityManager.createNativeQuery("""
            SELECT u.account_status, COUNT(*)
            FROM users u
            GROUP BY u.account_status
            ORDER BY u.account_status
            """)
            .getResultList()
            .stream()
            .map(result -> {
                Object[] row = (Object[]) result;
                return new StatusCountRow((String) row[0], number(row[1]));
            })
            .toList();
    }

    @Override
    public List<RecentAdminActionRow> findRecentAdminActions(int limit) {
        String createdAtInstant = AnalyticsTimeZone.toUtcInstant("a.created_at");
        List<?> results = entityManager.createNativeQuery("""
            SELECT a.admin_audit_log_id,
                   COALESCE(actor.email, 'system'),
                   a.action,
                   COALESCE(
                       target.email,
                       CASE
                           WHEN a.target_type IS NULL AND a.target_id IS NULL THEN '—'
                           WHEN a.target_id IS NULL THEN a.target_type
                           ELSE CONCAT(a.target_type, ':', a.target_id::text)
                       END
                   ),
                   %s
            FROM admin_audit_logs a
            LEFT JOIN admin_users admin ON admin.admin_user_id = a.admin_user_id
            LEFT JOIN users actor ON actor.user_id = admin.user_id
            LEFT JOIN users target ON a.target_type = 'USER' AND target.user_id = a.target_id
            ORDER BY a.created_at DESC
            LIMIT :limit
            """.formatted(createdAtInstant))
            .setParameter("limit", limit)
            .getResultList();

        List<RecentAdminActionRow> rows = new ArrayList<>(results.size());
        for (Object result : results) {
            Object[] row = (Object[]) result;
            rows.add(new RecentAdminActionRow(
                uuid(row[0]),
                (String) row[1],
                (String) row[2],
                (String) row[3],
                instant(row[4])
            ));
        }
        return rows;
    }

    @Override
    public List<RecentRegistrationRow> findRecentRegistrations(int limit) {
        String createdAtInstant = AnalyticsTimeZone.toUtcInstant("u.created_at");
        List<?> results = entityManager.createNativeQuery("""
            SELECT u.user_id,
                   u.avatar_url,
                   u.full_name,
                   u.email,
                   u.university_name,
                   u.account_status,
                   %s
            FROM users u
            WHERE u.account_status <> 'DELETED'
              AND u.deleted_at IS NULL
            ORDER BY u.created_at DESC
            LIMIT :limit
            """.formatted(createdAtInstant))
            .setParameter("limit", limit)
            .getResultList();

        List<RecentRegistrationRow> rows = new ArrayList<>(results.size());
        for (Object result : results) {
            Object[] row = (Object[]) result;
            rows.add(new RecentRegistrationRow(
                uuid(row[0]),
                (String) row[1],
                (String) row[2],
                (String) row[3],
                (String) row[4],
                (String) row[5],
                instant(row[6])
            ));
        }
        return rows;
    }

    private static Timestamp utcTimestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private static long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        if (value instanceof java.time.LocalDate date) {
            return date;
        }
        if (value instanceof java.time.LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        if (value instanceof Timestamp timestamp) {
            // A PostgreSQL date projection can be materialised as a timestamp by a provider.
            return timestamp.toLocalDateTime().toLocalDate();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(AnalyticsTimeZone.ZONE).toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private static UUID uuid(Object value) {
        return value instanceof UUID id ? id : UUID.fromString(String.valueOf(value));
    }

    private static Instant instant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.LocalDateTime dateTime) {
            return dateTime.toInstant(java.time.ZoneOffset.UTC);
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        return Instant.parse(String.valueOf(value));
    }
}
