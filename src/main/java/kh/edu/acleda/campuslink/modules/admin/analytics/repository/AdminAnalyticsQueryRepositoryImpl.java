package kh.edu.acleda.campuslink.modules.admin.analytics.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import kh.edu.acleda.campuslink.modules.admin.analytics.AnalyticsTimeZone;
import kh.edu.acleda.campuslink.modules.admin.analytics.dto.request.AdminAnalyticsQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL read-only aggregates for the fixed Phase 6 analytics dashboards. */
@Repository
@Transactional(readOnly = true)
public class AdminAnalyticsQueryRepositoryImpl implements AdminAnalyticsQueryRepository {

    private static final String ACTIVE_USER_SCOPE =
        "u.deleted_at IS NULL AND u.account_status <> 'DELETED'";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<SeriesRow> findUserGrowth(AdminAnalyticsQuery query) {
        String bucket = bucketExpression("u.created_at", query);
        String sql = """
            WITH buckets AS (
                SELECT generate_series(
                    CAST(:bucketFrom AS date),
                    CAST(:bucketTo AS date),
                    %s
                )::date AS bucket
            ), daily AS (
                SELECT %s::date AS bucket, COUNT(*) AS registrations
                FROM users u
                WHERE u.created_at >= :fromInstant
                  AND u.created_at < :toInstant
                  AND %s
                GROUP BY 1
            ), baseline AS (
                SELECT COUNT(*) AS count
                FROM users u
                WHERE u.created_at < :fromInstant
                  AND %s
            )
            SELECT buckets.bucket,
                   baseline.count
                       + SUM(COALESCE(daily.registrations, 0)) OVER (
                           ORDER BY buckets.bucket
                           ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                       )
            FROM buckets
            CROSS JOIN baseline
            LEFT JOIN daily ON daily.bucket = buckets.bucket
            ORDER BY buckets.bucket
            """.formatted(query.sqlInterval(), bucket, ACTIVE_USER_SCOPE, ACTIVE_USER_SCOPE);

        return querySeries(sql, query);
    }

    @Override
    public List<ActiveUsersRow> findActiveUsers(AdminAnalyticsQuery query) {
        String securityLocal = AnalyticsTimeZone.toLocal("sa.created_at");
        String refreshLocal = AnalyticsTimeZone.toLocal("rt.last_used_at");
        String sql = """
            WITH buckets AS (
                SELECT generate_series(
                    CAST(:bucketFrom AS date),
                    CAST(:bucketTo AS date),
                    %s
                )::date AS bucket
            ), activity AS (
                SELECT sa.user_id, %s AS local_at
                FROM security_audit_logs sa
                JOIN users u ON u.user_id = sa.user_id
                WHERE sa.event_type = 'LOGIN_SUCCESS'
                  AND COALESCE(sa.success, TRUE) = TRUE
                  AND sa.user_id IS NOT NULL
                  AND sa.created_at >= :activityFrom
                  AND sa.created_at < :activityTo
                  AND %s
                UNION ALL
                SELECT rt.user_id, %s AS local_at
                FROM refresh_tokens rt
                JOIN users u ON u.user_id = rt.user_id
                WHERE rt.last_used_at IS NOT NULL
                  AND rt.last_used_at >= :activityFrom
                  AND rt.last_used_at < :activityTo
                  AND %s
            ), effective AS (
                SELECT buckets.bucket,
                       GREATEST(
                           buckets.bucket::timestamp,
                           CAST(:fromDate AS timestamp)
                       ) AS start_at,
                       LEAST(
                           buckets.bucket::timestamp + %s,
                           CAST(:toExclusiveDate AS timestamp)
                       ) AS end_at
                FROM buckets
            )
            SELECT effective.bucket,
                   COUNT(DISTINCT activity.user_id) FILTER (
                       WHERE activity.local_at >= effective.start_at
                         AND activity.local_at < effective.end_at
                   ),
                   COUNT(DISTINCT activity.user_id) FILTER (
                       WHERE activity.local_at >= effective.end_at - INTERVAL '29 days'
                         AND activity.local_at < effective.end_at
                   )
            FROM effective
            LEFT JOIN activity
              ON activity.local_at >= effective.end_at - INTERVAL '29 days'
             AND activity.local_at < effective.end_at
            GROUP BY effective.bucket
            ORDER BY effective.bucket
            """.formatted(
            query.sqlInterval(), securityLocal, ACTIVE_USER_SCOPE, refreshLocal,
            ACTIVE_USER_SCOPE, query.sqlInterval());

        Query nativeQuery = entityManager.createNativeQuery(sql);
        setBucketParameters(nativeQuery, query);
        nativeQuery
            .setParameter("fromDate", Date.valueOf(query.from()))
            .setParameter("toExclusiveDate", Date.valueOf(query.to().plusDays(1)))
            .setParameter("activityFrom", timestamp(query.from().minusDays(29)))
            .setParameter("activityTo", timestamp(query.toExclusiveInstant()));

        return nativeQuery.getResultList().stream()
            .map(result -> {
                Object[] row = row(result);
                return new ActiveUsersRow(
                    toLocalDate(row[0]), number(row[1]), number(row[2]));
            })
            .toList();
    }

    @Override
    public List<RegistrationRow> findRegistrations(AdminAnalyticsQuery query) {
        String bucket = bucketExpression("u.created_at", query);
        String sql = """
            WITH buckets AS (
                SELECT generate_series(
                    CAST(:bucketFrom AS date),
                    CAST(:bucketTo AS date),
                    %s
                )::date AS bucket
            ), registrations AS (
                SELECT %s::date AS bucket,
                       COUNT(*) FILTER (WHERE u.auth_provider = 'LOCAL') AS local_count,
                       COUNT(*) FILTER (WHERE u.auth_provider = 'GOOGLE') AS google_count,
                       COUNT(*) FILTER (
                           WHERE u.auth_provider NOT IN ('LOCAL', 'GOOGLE')
                              OR u.auth_provider IS NULL
                       ) AS other_count,
                       COUNT(*) AS total_count
                FROM users u
                WHERE u.created_at >= :fromInstant
                  AND u.created_at < :toInstant
                  AND %s
                GROUP BY 1
            )
            SELECT buckets.bucket,
                   COALESCE(registrations.local_count, 0),
                   COALESCE(registrations.google_count, 0),
                   COALESCE(registrations.other_count, 0),
                   COALESCE(registrations.total_count, 0)
            FROM buckets
            LEFT JOIN registrations ON registrations.bucket = buckets.bucket
            ORDER BY buckets.bucket
            """.formatted(query.sqlInterval(), bucket, ACTIVE_USER_SCOPE);

        Query nativeQuery = entityManager.createNativeQuery(sql);
        setBucketAndInstantParameters(nativeQuery, query);
        return nativeQuery.getResultList().stream()
            .map(result -> {
                Object[] row = row(result);
                return new RegistrationRow(
                    toLocalDate(row[0]),
                    number(row[1]),
                    number(row[2]),
                    number(row[3]),
                    number(row[4]));
            })
            .toList();
    }

    @Override
    public DistributionResult findUserStatus(AdminAnalyticsQuery query) {
        return new DistributionResult(
            findDistribution(query, "COALESCE(u.account_status, 'UNKNOWN')", false),
            findTrend(query, "COALESCE(u.account_status, 'UNKNOWN')", false));
    }

    @Override
    public List<DistributionRow> findUniversities(AdminAnalyticsQuery query) {
        return findDistribution(query, universityExpression(), true);
    }

    @Override
    public List<DistributionRow> findWorkStatus(AdminAnalyticsQuery query) {
        return findDistribution(query, "COALESCE(u.work_status, 'UNKNOWN')", true);
    }

    @Override
    public XpDistributionResult findXpDistribution(AdminAnalyticsQuery query) {
        String bucket = """
            CASE
                WHEN u.total_xp BETWEEN 0 AND 99 THEN '0-99'
                WHEN u.total_xp BETWEEN 100 AND 499 THEN '100-499'
                WHEN u.total_xp BETWEEN 500 AND 999 THEN '500-999'
                WHEN u.total_xp BETWEEN 1000 AND 4999 THEN '1k-4.9k'
                WHEN u.total_xp >= 5000 THEN '5k+'
                ELSE 'Unknown'
            END
            """;
        String order = """
            CASE
                WHEN u.total_xp BETWEEN 0 AND 99 THEN 1
                WHEN u.total_xp BETWEEN 100 AND 499 THEN 2
                WHEN u.total_xp BETWEEN 500 AND 999 THEN 3
                WHEN u.total_xp BETWEEN 1000 AND 4999 THEN 4
                WHEN u.total_xp >= 5000 THEN 5
                ELSE 6
            END
            """;
        String sql = """
            SELECT %s AS xp_bucket, COUNT(*)
            FROM users u
            WHERE u.created_at >= :fromInstant
              AND u.created_at < :toInstant
              AND %s
            GROUP BY 1
            ORDER BY MIN(%s)
            """.formatted(bucket, ACTIVE_USER_SCOPE, order);

        Query nativeQuery = entityManager.createNativeQuery(sql);
        setInstantParameters(nativeQuery, query);
        List<DistributionRow> buckets = nativeQuery.getResultList().stream()
            .map(result -> {
                Object[] row = row(result);
                return new DistributionRow(text(row[0]), number(row[1]));
            })
            .toList();

        String levelSql = """
            SELECT u.level, COUNT(*)
            FROM users u
            WHERE u.created_at >= :fromInstant
              AND u.created_at < :toInstant
              AND %s
            GROUP BY u.level
            ORDER BY u.level
            """.formatted(ACTIVE_USER_SCOPE);
        Query levelQuery = entityManager.createNativeQuery(levelSql);
        setInstantParameters(levelQuery, query);
        List<LevelRow> levels = levelQuery.getResultList().stream()
            .map(result -> {
                Object[] row = row(result);
                return new LevelRow(Math.toIntExact(number(row[0])), number(row[1]));
            })
            .toList();

        return new XpDistributionResult(buckets, levels);
    }

    @Override
    public DistributionResult findLanguages(AdminAnalyticsQuery query) {
        return new DistributionResult(
            findDistribution(query, "COALESCE(u.preferred_language, 'UNKNOWN')", true),
            findTrend(query, "COALESCE(u.preferred_language, 'UNKNOWN')", true));
    }

    @Override
    public List<DistributionRow> findPlatforms(AdminAnalyticsQuery query) {
        String platform = """
            CASE
                WHEN POSITION('android' IN LOWER(CONCAT_WS(' ', rt.user_agent, rt.device_info))) > 0
                    THEN 'Android'
                WHEN POSITION('iphone' IN LOWER(CONCAT_WS(' ', rt.user_agent, rt.device_info))) > 0
                  OR POSITION('ipad' IN LOWER(CONCAT_WS(' ', rt.user_agent, rt.device_info))) > 0
                  OR POSITION('ios' IN LOWER(CONCAT_WS(' ', rt.user_agent, rt.device_info))) > 0
                    THEN 'iOS'
                WHEN POSITION('windows' IN LOWER(CONCAT_WS(' ', rt.user_agent, rt.device_info))) > 0
                  OR POSITION('macintosh' IN LOWER(CONCAT_WS(' ', rt.user_agent, rt.device_info))) > 0
                  OR POSITION('linux' IN LOWER(CONCAT_WS(' ', rt.user_agent, rt.device_info))) > 0
                  OR POSITION('web' IN LOWER(CONCAT_WS(' ', rt.user_agent, rt.device_info))) > 0
                    THEN 'Web'
                ELSE 'Unknown'
            END
            """;
        String sql = """
            SELECT platform, COUNT(DISTINCT user_id)
            FROM (
                SELECT rt.user_id, %s AS platform
                FROM refresh_tokens rt
                JOIN users u ON u.user_id = rt.user_id
                WHERE COALESCE(rt.last_used_at, rt.created_at) >= :fromInstant
                  AND COALESCE(rt.last_used_at, rt.created_at) < :toInstant
                  AND %s
            ) platform_rows
            GROUP BY platform
            ORDER BY COUNT(DISTINCT user_id) DESC, platform
            """.formatted(platform, ACTIVE_USER_SCOPE);

        Query nativeQuery = entityManager.createNativeQuery(sql);
        setInstantParameters(nativeQuery, query);
        return nativeQuery.getResultList().stream()
            .map(result -> {
                Object[] row = row(result);
                return new DistributionRow(text(row[0]), number(row[1]));
            })
            .toList();
    }

    private List<SeriesRow> querySeries(String sql, AdminAnalyticsQuery query) {
        Query nativeQuery = entityManager.createNativeQuery(sql);
        setBucketAndInstantParameters(nativeQuery, query);
        return nativeQuery.getResultList().stream()
            .map(result -> {
                Object[] row = row(result);
                return new SeriesRow(toLocalDate(row[0]), number(row[1]));
            })
            .toList();
    }

    private List<DistributionRow> findDistribution(
        AdminAnalyticsQuery query,
        String labelExpression,
        boolean excludeDeleted) {
        String sql = """
            SELECT %s AS label, COUNT(*)
            FROM users u
            WHERE u.created_at >= :fromInstant
              AND u.created_at < :toInstant
              AND %s
            GROUP BY 1
            ORDER BY COUNT(*) DESC, label
            """.formatted(labelExpression, userScope(excludeDeleted));
        Query nativeQuery = entityManager.createNativeQuery(sql);
        setInstantParameters(nativeQuery, query);
        return nativeQuery.getResultList().stream()
            .map(result -> {
                Object[] row = row(result);
                return new DistributionRow(text(row[0]), number(row[1]));
            })
            .toList();
    }

    private List<TrendRow> findTrend(
        AdminAnalyticsQuery query,
        String labelExpression,
        boolean excludeDeleted) {
        String bucket = bucketExpression("u.created_at", query);
        String sql = """
            SELECT %s::date AS bucket, %s AS label, COUNT(*)
            FROM users u
            WHERE u.created_at >= :fromInstant
              AND u.created_at < :toInstant
              AND %s
            GROUP BY 1, 2
            ORDER BY 1, 2
            """.formatted(bucket, labelExpression, userScope(excludeDeleted));
        Query nativeQuery = entityManager.createNativeQuery(sql);
        setInstantParameters(nativeQuery, query);
        return nativeQuery.getResultList().stream()
            .map(result -> {
                Object[] row = row(result);
                return new TrendRow(toLocalDate(row[0]), text(row[1]), number(row[2]));
            })
            .toList();
    }

    private static String bucketExpression(String column, AdminAnalyticsQuery query) {
        return switch (query.granularity()) {
            case DAY -> AnalyticsTimeZone.dayBucket(column);
            case WEEK -> AnalyticsTimeZone.weekBucket(column);
            case MONTH -> AnalyticsTimeZone.monthBucket(column);
        };
    }

    private static String universityExpression() {
        return "COALESCE(NULLIF(BTRIM(u.university_name), ''), 'Unknown')";
    }

    private static String userScope(boolean excludeDeleted) {
        return excludeDeleted ? ACTIVE_USER_SCOPE : "TRUE";
    }

    private static void setBucketParameters(Query queryObject, AdminAnalyticsQuery query) {
        queryObject
            .setParameter("bucketFrom", Date.valueOf(query.bucketFrom()))
            .setParameter("bucketTo", Date.valueOf(query.bucketTo()));
    }

    private static void setInstantParameters(Query queryObject, AdminAnalyticsQuery query) {
        queryObject
            .setParameter("fromInstant", timestamp(query.fromInstant()))
            .setParameter("toInstant", timestamp(query.toExclusiveInstant()));
    }

    private static void setBucketAndInstantParameters(Query queryObject, AdminAnalyticsQuery query) {
        setBucketParameters(queryObject, query);
        setInstantParameters(queryObject, query);
    }

    private static Timestamp timestamp(java.time.Instant instant) {
        return Timestamp.from(instant);
    }

    private static Timestamp timestamp(LocalDate localDate) {
        return timestamp(AnalyticsTimeZone.startOfDay(localDate));
    }

    private static Object[] row(Object result) {
        return result instanceof Object[] values ? values : new Object[]{result};
    }

    private static String text(Object value) {
        return value == null ? "Unknown" : String.valueOf(value);
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
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.time.LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(AnalyticsTimeZone.ZONE).toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }
}
