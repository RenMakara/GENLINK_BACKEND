package kh.edu.acleda.campuslink.modules.admin.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.repository.XpHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** PostgreSQL regression coverage for the ADM-064 XP aggregation and ledger queries. */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "GENLINK_TEST_POSTGRES_URL", matches = ".+")
class AdminUserXpRepositoryPostgresTest {

    @Autowired
    private XpHistoryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> fixtureUsers = new ArrayList<>();
    private UUID targetUserId;

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
        targetUserId = insertUser("target", "2026-09-20 12:00:00", "adm064-target@example.com");
        UUID otherUserId = insertUser("other", "2026-09-20 13:00:00", "adm064-other@example.com");

        insertXp(targetUserId, "QUIZ", 300, "2026-09-22 10:00:00");
        insertXp(targetUserId, "ADMIN_ADJUSTMENT", -25, "2026-09-23 10:00:00");
        insertXp(otherUserId, "QUIZ", 1000, "2026-09-24 10:00:00");
    }

    @AfterEach
    void removeFixtures() {
        for (UUID userId : fixtureUsers) {
            jdbcTemplate.update("DELETE FROM xp_history WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);
        }
        fixtureUsers.clear();
    }

    @Test
    void aggregatesSignedXpBySourceAndKeepsTheTargetOwnerScope() {
        List<XpHistoryRepository.SourceBreakdownProjection> breakdown = repository
            .summarizeByUserId(targetUserId);

        assertThat(breakdown).hasSize(2);
        assertThat(breakdown).extracting(XpHistoryRepository.SourceBreakdownProjection::getSourceType)
            .containsExactly("QUIZ", "ADMIN_ADJUSTMENT");
        assertThat(breakdown).extracting(XpHistoryRepository.SourceBreakdownProjection::getXpAmount)
            .containsExactly(300L, -25L);
        assertThat(breakdown.stream().mapToLong(
            projection -> projection.getXpAmount() == null ? 0 : projection.getXpAmount()).sum())
            .isEqualTo(275L);
    }

    @Test
    void returnsSignedLedgerNewestFirstAndPaged() {
        Page<kh.edu.acleda.campuslink.modules.entertainment.entity.XpHistory> page = repository
            .findPageByUserId(targetUserId, PageRequest.of(0, 25));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(
            kh.edu.acleda.campuslink.modules.entertainment.entity.XpHistory::getXpAmount)
            .containsExactly(-25, 300);
    }

    private UUID insertUser(String label, String createdAt, String email) {
        UUID userId = UUID.randomUUID();
        String suffix = userId.toString().replace("-", "");
        jdbcTemplate.update("""
            INSERT INTO users (
                user_id, full_name, username, email, account_status, created_at, deleted_at
            ) VALUES (?, ?, ?, ?, 'ACTIVE', CAST(? AS TIMESTAMP), NULL)
            """,
            userId,
            "ADM-064 " + label,
            "adm064_" + suffix,
            email,
            createdAt);
        fixtureUsers.add(userId);
        return userId;
    }

    private void insertXp(UUID userId, String sourceType, int amount, String createdAt) {
        jdbcTemplate.update("""
            INSERT INTO xp_history (
                xp_history_id, user_id, source_type, source_id, xp_amount, created_at
            ) VALUES (?, ?, ?, NULL, ?, CAST(? AS TIMESTAMP))
            """,
            UUID.randomUUID(), userId, sourceType, amount, createdAt);
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
