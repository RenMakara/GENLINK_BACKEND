package kh.edu.acleda.campuslink.modules.admin.analytics.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.analytics.dto.request.AdminAnalyticsQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL regression coverage for the nine ADM-113 projection queries. */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "GENLINK_TEST_POSTGRES_URL", matches = ".+")
class AdminAnalyticsQueryRepositoryPostgresTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 22);
    private static final LocalDate TO = LocalDate.of(2026, 9, 23);

    @Autowired
    private AdminAnalyticsQueryRepository queryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> fixtureUsers = new ArrayList<>();

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnv("GENLINK_TEST_POSTGRES_URL"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username",
            () -> envOrDefault("GENLINK_TEST_POSTGRES_USERNAME", "postgres"));
        registry.add("spring.datasource.password",
            () -> envOrDefault("GENLINK_TEST_POSTGRES_PASSWORD", "postgres"));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.change-log",
            () -> "classpath:db/changelog/db.changelog-master.yaml");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @BeforeEach
    void seedFixtures() {
        insertUser("baseline", "ACTIVE", "2026-01-01 00:00:00", "LOCAL", "University A", "STUDENT",
            "ENGLISH", 50, 1, null);
        UUID localUser = insertUser(
            "local", "ACTIVE", "2026-09-22 16:59:59", "LOCAL", "", "FREELANCER",
            "ENGLISH", 150, 2, null);
        UUID googleUser = insertUser(
            "google", "ACTIVE", "2026-09-22 17:00:00", "GOOGLE", "University A", "STUDENT",
            "KHMER", 700, 8, null);
        UUID inactiveUser = insertUser(
            "failed", "ACTIVE", "2026-09-22 17:30:00", "LOCAL", "University B", "OTHER",
            "ENGLISH", 6000, 60, null);
        UUID deletedUser = insertUser(
            "deleted", "DELETED", "2026-09-23 01:00:00", "GOOGLE", "University C", "STUDENT",
            "KHMER", 100, 2, "2026-09-23 01:00:00");

        // 17:00Z is midnight in Phnom Penh. These records verify the local-day boundary and
        // distinct-user union across login and refresh activity.
        insertLogin(localUser, "2026-09-22 16:59:59", true);
        insertRefresh(localUser, "2026-09-22 16:59:58", "Android");
        insertLogin(googleUser, "2026-09-22 17:00:00", true);
        insertRefresh(googleUser, "2026-09-22 17:00:01", "iPhone");
        insertLogin(inactiveUser, "2026-09-22 18:00:00", false);
        insertLogin(deletedUser, "2026-09-22 19:00:00", true);
    }

    @AfterEach
    void removeFixtures() {
        for (UUID userId : fixtureUsers) {
            jdbcTemplate.update("DELETE FROM security_audit_logs WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);
        }
        fixtureUsers.clear();
    }

    @Test
    void usesPhnomPenhBoundariesAndKeepsAggregatesReadOnlyAndTyped() {
        AdminAnalyticsQuery query = AdminAnalyticsQuery.fromRequest(FROM, TO, "day");

        List<AdminAnalyticsQueryRepository.SeriesRow> growth = queryRepository.findUserGrowth(query);
        assertThat(growth).extracting(AdminAnalyticsQueryRepository.SeriesRow::bucket)
            .containsExactly(FROM, TO);
        assertThat(growth).extracting(AdminAnalyticsQueryRepository.SeriesRow::count)
            .containsExactly(2L, 4L);

        List<AdminAnalyticsQueryRepository.RegistrationRow> registrations =
            queryRepository.findRegistrations(query);
        assertThat(registrations).extracting(AdminAnalyticsQueryRepository.RegistrationRow::bucket)
            .containsExactly(FROM, TO);
        assertThat(registrations.get(0).local()).isEqualTo(1L);
        assertThat(registrations.get(0).google()).isEqualTo(0L);
        assertThat(registrations.get(0).total()).isEqualTo(1L);
        assertThat(registrations.get(1).local()).isEqualTo(1L);
        assertThat(registrations.get(1).google()).isEqualTo(1L);
        assertThat(registrations.get(1).total()).isEqualTo(2L);

        List<AdminAnalyticsQueryRepository.ActiveUsersRow> activity =
            queryRepository.findActiveUsers(query);
        assertThat(activity).extracting(AdminAnalyticsQueryRepository.ActiveUsersRow::dau)
            .containsExactly(1L, 1L);
        assertThat(activity).extracting(AdminAnalyticsQueryRepository.ActiveUsersRow::mau)
            .containsExactly(1L, 2L);

        List<AdminAnalyticsQueryRepository.DistributionRow> platforms =
            queryRepository.findPlatforms(query);
        assertThat(platforms).containsExactlyInAnyOrder(
            new AdminAnalyticsQueryRepository.DistributionRow("Android", 1L),
            new AdminAnalyticsQueryRepository.DistributionRow("iOS", 1L));

        AdminAnalyticsQueryRepository.DistributionResult statuses =
            queryRepository.findUserStatus(query);
        assertThat(statuses.distribution()).contains(
            new AdminAnalyticsQueryRepository.DistributionRow("ACTIVE", 3L),
            new AdminAnalyticsQueryRepository.DistributionRow("DELETED", 1L));
        assertThat(statuses.trend()).extracting(AdminAnalyticsQueryRepository.TrendRow::bucket)
            .contains(FROM, TO);

        assertThat(queryRepository.findUniversities(query)).contains(
            new AdminAnalyticsQueryRepository.DistributionRow("University A", 1L),
            new AdminAnalyticsQueryRepository.DistributionRow("Unknown", 1L));
        assertThat(queryRepository.findWorkStatus(query)).contains(
            new AdminAnalyticsQueryRepository.DistributionRow("STUDENT", 1L),
            new AdminAnalyticsQueryRepository.DistributionRow("FREELANCER", 1L));
        assertThat(queryRepository.findLanguages(query).distribution()).contains(
            new AdminAnalyticsQueryRepository.DistributionRow("ENGLISH", 2L),
            new AdminAnalyticsQueryRepository.DistributionRow("KHMER", 1L));
        assertThat(queryRepository.findXpDistribution(query).buckets()).contains(
            new AdminAnalyticsQueryRepository.DistributionRow("100-499", 1L),
            new AdminAnalyticsQueryRepository.DistributionRow("500-999", 1L));
    }

    @Test
    @Transactional
    void confirmsGrowthIndexIsAvailableToThePostgresPlanner() {
        List<String> indexes = jdbcTemplate.query(
            """
            SELECT indexname
            FROM pg_indexes
            WHERE schemaname = current_schema()
              AND indexname IN (
                'idx_users_created_at_status',
                'idx_users_university',
                'idx_refresh_tokens_last_used'
              )
            """,
            (resultSet, rowNumber) -> resultSet.getString("indexname"));
        assertThat(indexes).contains(
            "idx_users_created_at_status",
            "idx_users_university",
            "idx_refresh_tokens_last_used");

        List<String> measuredPlan = jdbcTemplate.query(
            """
            EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
            SELECT COUNT(*)
            FROM users u
            WHERE u.created_at >= TIMESTAMP '2026-09-22 00:00:00'
              AND u.created_at < TIMESTAMP '2026-09-24 00:00:00'
              AND u.account_status <> 'DELETED'
              AND u.deleted_at IS NULL
            """,
            (resultSet, rowNumber) -> resultSet.getString(1));
        assertThat(measuredPlan).anyMatch(line -> line.startsWith("Execution Time:"));
        System.out.println("ADM-114 growth query EXPLAIN ANALYZE:\n"
            + String.join("\n", measuredPlan));

        // This is a planner usability check, not a production session setting.
        jdbcTemplate.execute("SET LOCAL enable_seqscan = off");
        String plan = String.join("\n", jdbcTemplate.query(
            """
            EXPLAIN (FORMAT TEXT)
            SELECT COUNT(*)
            FROM users u
            WHERE u.created_at >= TIMESTAMP '2026-09-22 00:00:00'
              AND u.created_at < TIMESTAMP '2026-09-24 00:00:00'
              AND u.account_status <> 'DELETED'
              AND u.deleted_at IS NULL
            """,
            (resultSet, rowNumber) -> resultSet.getString(1)));
        assertThat(plan).contains("idx_users_created_at_status");
    }

    private UUID insertUser(
        String label,
        String status,
        String createdAt,
        String authProvider,
        String university,
        String workStatus,
        String language,
        int totalXp,
        int level,
        String deletedAt) {
        UUID userId = UUID.randomUUID();
        String suffix = userId.toString().replace("-", "");
        jdbcTemplate.update("""
            INSERT INTO users (
                user_id, full_name, username, email, account_status, created_at, deleted_at,
                auth_provider, university_name, work_status, preferred_language, total_xp, level
            ) VALUES (?, ?, ?, ?, ?, CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP), ?, ?, ?, ?, ?, ?)
            """,
            userId,
            "ADM-113 " + label,
            "adm113_" + suffix,
            "adm113_" + suffix + "@example.com",
            status,
            createdAt,
            deletedAt,
            authProvider,
            university,
            workStatus,
            language,
            totalXp,
            level);
        fixtureUsers.add(userId);
        return userId;
    }

    private void insertLogin(UUID userId, String createdAt, boolean success) {
        jdbcTemplate.update("""
            INSERT INTO security_audit_logs (id, event_type, user_id, created_at, success)
            VALUES (?, 'LOGIN_SUCCESS', ?, CAST(? AS TIMESTAMP), ?)
            """,
            UUID.randomUUID(), userId, createdAt, success);
    }

    private void insertRefresh(UUID userId, String lastUsedAt, String deviceInfo) {
        jdbcTemplate.update("""
            INSERT INTO refresh_tokens (
                refresh_token_id, user_id, token_hash, expires_at, last_used_at, device_info
            ) VALUES (?, ?, ?, CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP), ?)
            """,
            UUID.randomUUID(), userId, "adm113_" + UUID.randomUUID(),
            "2030-01-01 00:00:00", lastUsedAt, deviceInfo);
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set for the PostgreSQL integration test");
        }
        return value;
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }
}
