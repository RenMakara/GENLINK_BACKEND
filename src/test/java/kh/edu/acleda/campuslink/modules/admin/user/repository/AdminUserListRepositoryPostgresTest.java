package kh.edu.acleda.campuslink.modules.admin.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserListCriteria;
import kh.edu.acleda.campuslink.modules.user.entity.User;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/** PostgreSQL regression coverage for the ADM-059 Criteria query. */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "GENLINK_TEST_POSTGRES_URL", matches = ".+")
class AdminUserListRepositoryPostgresTest {

    @Autowired
    private AdminUserListRepository repository;

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
    }

    @BeforeEach
    void seedFixtures() {
        UUID active = insertUser("active", "ACTIVE", "2026-09-22 12:00:00", "active@example.com");
        UUID pending = insertUser("pending", "PENDING", "2026-09-23 12:00:00", "pending@example.com");
        UUID deleted = insertUser("deleted", "DELETED", "2026-09-23 12:00:00", "deleted@example.com");

        // Keep activity outside the dashboard regression window.
        insertRefresh(active, "2030-09-23 12:00:00");
        insertRefresh(pending, "2030-09-23 13:00:00");
        insertRefresh(deleted, "2030-09-23 14:00:00");
    }

    @AfterEach
    void removeFixtures() {
        for (UUID userId : fixtureUsers) {
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);
        }
        fixtureUsers.clear();
    }

    @Test
    void executesTheDirectoryQueryAgainstPostgresWithoutNullableParameterGuards() {
        AdminUserListCriteria criteria = AdminUserListCriteria.fromRequest(
            "example.com", "ACTIVE,PENDING", null, null, null, null, null, null,
            null, null, null, null, LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 23),
            null, false, false, 0, 25, List.of("lastActiveAt,desc"));

        Page<User> page = repository.findPage(criteria, PageRequest.of(0, 25));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(User::getEmail)
            .containsExactly("pending@example.com", "active@example.com");
    }

    @Test
    void canExplicitlyIncludeSoftDeletedUsers() {
        AdminUserListCriteria criteria = AdminUserListCriteria.fromRequest(
            null, "DELETED", null, null, null, null, null, null,
            null, null, null, null, null, null, null,
            false, true, 0, 25, null);

        Page<User> page = repository.findPage(criteria, PageRequest.of(0, 25));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(User::getEmail)
            .containsExactly("deleted@example.com");
    }

    private UUID insertUser(String label, String status, String createdAt, String email) {
        UUID userId = UUID.randomUUID();
        String suffix = userId.toString().replace("-", "");
        jdbcTemplate.update("""
            INSERT INTO users (
                user_id, full_name, username, email, account_status, created_at, deleted_at
            ) VALUES (?, ?, ?, ?, ?, CAST(? AS TIMESTAMP), NULL)
            """,
            userId,
            "ADM-059 " + label,
            "adm059_" + suffix,
            email,
            status,
            createdAt);
        if ("DELETED".equals(status)) {
            jdbcTemplate.update(
                "UPDATE users SET deleted_at = CAST(? AS TIMESTAMP) WHERE user_id = ?",
                createdAt, userId);
        }
        fixtureUsers.add(userId);
        return userId;
    }

    private void insertRefresh(UUID userId, String lastUsedAt) {
        jdbcTemplate.update("""
            INSERT INTO refresh_tokens (
                refresh_token_id, user_id, token_hash, expires_at, last_used_at
            ) VALUES (?, ?, ?, CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP))
            """,
            UUID.randomUUID(), userId, "adm059_" + UUID.randomUUID(),
            "2030-01-01 00:00:00", lastUsedAt);
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
