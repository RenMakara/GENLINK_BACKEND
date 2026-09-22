package kh.edu.acleda.campuslink.modules.admin.analytics.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.analytics.repository.AdminDashboardQueryRepository.ActivitySnapshot;
import kh.edu.acleda.campuslink.modules.admin.analytics.repository.AdminDashboardQueryRepository.ActivityTrendRow;
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

/**
 * PostgreSQL regression coverage for the DAU/MAU proxy.
 *
 * <p>The dashboard query deliberately uses PostgreSQL's date-series and timestamp operators, so
 * H2 is not an adequate proof of the production query. CI supplies a disposable PostgreSQL URL;
 * local runs without that variable keep the normal test suite independent of a database server.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "GENLINK_TEST_POSTGRES_URL", matches = ".+")
class AdminDashboardQueryRepositoryPostgresTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 23);

    @Autowired
    private AdminDashboardQueryRepository queryRepository;

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
    void seedActivityFixtures() {
        UUID returningUser = insertUser("returning", "ACTIVE", null);
        UUID secondUser = insertUser("second", "ACTIVE", null);
        UUID failedLoginUser = insertUser("failed", "ACTIVE", null);
        UUID deletedUser = insertUser("deleted", "DELETED", TODAY + " 00:00:00");

        // 17:00Z is midnight in Phnom Penh. The same user appears on both sides of the
        // boundary, and also has a refresh on the current day.
        insertLogin(returningUser, "2026-09-22 16:59:59", true);
        insertLogin(returningUser, "2026-09-22 17:00:00", true);
        insertRefresh(returningUser, "2026-09-22 17:00:01");

        // A second login and refresh on the same local day must still count as one user.
        insertLogin(secondUser, "2026-09-23 16:59:59", true);
        insertRefresh(secondUser, "2026-09-23 16:59:58");

        // Failed logins and users already deleted from the product are not active users.
        insertLogin(failedLoginUser, "2026-09-23 10:00:00", false);
        insertLogin(deletedUser, "2026-09-23 11:00:00", true);
    }

    @AfterEach
    void removeActivityFixtures() {
        for (UUID userId : fixtureUsers) {
            jdbcTemplate.update("DELETE FROM security_audit_logs WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);
        }
        fixtureUsers.clear();
    }

    @Test
    void bucketsByPhnomPenhDayAndCountsEachUserOnceAcrossBothActivitySources() {
        List<ActivityTrendRow> rows = queryRepository.findActivityTrends(
            TODAY.minusDays(1), TODAY);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).date()).isEqualTo(TODAY.minusDays(1));
        assertThat(rows.get(0).dau()).isEqualTo(1L);
        assertThat(rows.get(0).mau()).isEqualTo(1L);
        assertThat(rows.get(1).date()).isEqualTo(TODAY);
        assertThat(rows.get(1).dau()).isEqualTo(2L);
        assertThat(rows.get(1).mau()).isEqualTo(2L);

        ActivitySnapshot snapshot = queryRepository.findActivitySnapshot(TODAY);
        assertThat(snapshot).isEqualTo(new ActivitySnapshot(2L, 1L, 2L, 1L));
    }

    private UUID insertUser(String label, String status, String deletedAt) {
        UUID userId = UUID.randomUUID();
        String suffix = userId.toString().replace("-", "");
        jdbcTemplate.update("""
            INSERT INTO users (
                user_id, full_name, username, email, account_status, created_at, deleted_at
            ) VALUES (?, ?, ?, ?, ?, CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP))
            """,
            userId,
            "ADM-046 " + label,
            "adm046_" + suffix,
            "adm046_" + suffix + "@example.com",
            status,
            "2026-01-01 00:00:00",
            deletedAt
        );
        fixtureUsers.add(userId);
        return userId;
    }

    private void insertLogin(UUID userId, String createdAt, boolean success) {
        jdbcTemplate.update("""
            INSERT INTO security_audit_logs (id, event_type, user_id, created_at, success)
            VALUES (?, 'LOGIN_SUCCESS', ?, CAST(? AS TIMESTAMP), ?)
            """,
            UUID.randomUUID(), userId, createdAt, success
        );
    }

    private void insertRefresh(UUID userId, String lastUsedAt) {
        String token = "adm046_" + UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO refresh_tokens (
                refresh_token_id, user_id, token_hash, expires_at, last_used_at
            ) VALUES (?, ?, ?, CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP))
            """,
            UUID.randomUUID(), userId, token, "2030-01-01 00:00:00", lastUsedAt
        );
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
