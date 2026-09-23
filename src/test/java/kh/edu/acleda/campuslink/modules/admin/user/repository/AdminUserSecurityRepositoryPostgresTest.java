package kh.edu.acleda.campuslink.modules.admin.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.auth.entity.RefreshToken;
import kh.edu.acleda.campuslink.modules.auth.entity.SecurityAuditLog;
import kh.edu.acleda.campuslink.modules.auth.repository.RefreshTokenRepository;
import kh.edu.acleda.campuslink.modules.auth.repository.SecurityAuditLogRepository;
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

/** PostgreSQL regression coverage for the ADM-063 session and login-history queries. */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "GENLINK_TEST_POSTGRES_URL", matches = ".+")
class AdminUserSecurityRepositoryPostgresTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private SecurityAuditLogRepository securityAuditLogRepository;

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
        targetUserId = insertUser("target", "2026-09-20 12:00:00", "adm063-target@example.com");
        UUID otherUserId = insertUser("other", "2026-09-20 13:00:00", "adm063-other@example.com");

        insertRefresh(targetUserId, "active", "2030-01-01 00:00:00", null,
            "2026-09-23 12:00:00", "2026-09-23 12:00:00");
        insertRefresh(targetUserId, "expired", "2020-01-01 00:00:00", null,
            "2026-09-23 13:00:00", "2026-09-23 13:00:00");
        insertRefresh(targetUserId, "revoked", "2030-01-01 00:00:00", "2026-09-23 14:00:00",
            "2026-09-23 14:00:00", "2026-09-23 14:00:00");
        insertRefresh(otherUserId, "other", "2030-01-01 00:00:00", null,
            "2026-09-23 15:00:00", "2026-09-23 15:00:00");

        insertAudit(targetUserId, "LOGIN_SUCCESS", "2026-09-22 10:00:00", true, null);
        insertAudit(targetUserId, "LOGIN_FAILED", "2026-09-23 10:00:00", false, "BAD_CREDENTIALS");
        insertAudit(targetUserId, "REFRESH_SUCCESS", "2026-09-24 10:00:00", true, null);
        insertAudit(otherUserId, "LOGIN_SUCCESS", "2026-09-25 10:00:00", true, null);
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
    void activeSessionsExcludeRevokedAndExpiredRowsAndStayOwnerScoped() {
        Page<RefreshToken> page = refreshTokenRepository.findActiveByUserId(
            targetUserId,
            Instant.parse("2026-09-24T00:00:00Z"),
            PageRequest.of(0, 25));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(RefreshToken::getTokenHash)
            .containsExactly("adm063-active");
    }

    @Test
    void loginHistoryIsNewestFirstAndPaginatedForOneOwner() {
        Page<SecurityAuditLog> page = securityAuditLogRepository.findByUserIdOrderByCreatedAtDesc(
            targetUserId, PageRequest.of(0, 25));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(SecurityAuditLog::getEventType)
            .containsExactly("REFRESH_SUCCESS", "LOGIN_FAILED", "LOGIN_SUCCESS");
        assertThat(page.getContent().get(1).getFailureReason()).isEqualTo("BAD_CREDENTIALS");
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
            "ADM-063 " + label,
            "adm063_" + suffix,
            email,
            createdAt);
        fixtureUsers.add(userId);
        return userId;
    }

    private void insertRefresh(
        UUID userId,
        String label,
        String expiresAt,
        String revokedAt,
        String lastUsedAt,
        String createdAt) {
        jdbcTemplate.update("""
            INSERT INTO refresh_tokens (
                refresh_token_id, user_id, token_hash, expires_at, revoked_at,
                device_info, ip_address, user_agent, last_used_at, created_at
            ) VALUES (?, ?, ?, CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP), ?, ?, ?,
                CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP))
            """,
            UUID.randomUUID(),
            userId,
            "adm063-" + label,
            expiresAt,
            revokedAt,
            "ADM-063 device",
            "192.0.2.10",
            "ADM-063 test agent",
            lastUsedAt,
            createdAt);
    }

    private void insertAudit(UUID userId, String eventType, String createdAt,
        boolean success, String failureReason) {
        jdbcTemplate.update("""
            INSERT INTO security_audit_logs (
                id, event_type, user_id, created_at, email, ip_address, user_agent,
                details, status, success, failure_reason
            ) VALUES (?, ?, ?, CAST(? AS TIMESTAMP), ?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            eventType,
            userId,
            createdAt,
            "adm063-test@example.com",
            "192.0.2.10",
            "ADM-063 test agent",
            "sensitive test details",
            success ? "SUCCESS" : "FAILURE",
            success,
            failureReason);
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
