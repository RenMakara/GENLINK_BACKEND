package kh.edu.acleda.campuslink.modules.admin.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.user.repository.AdminUniversityFacetRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** PostgreSQL regression coverage for the ADM-066 university facet query. */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "GENLINK_TEST_POSTGRES_URL", matches = ".+")
class AdminUniversityFacetRepositoryPostgresTest {

    @Autowired
    private AdminUniversityFacetRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> fixtureUsers = new ArrayList<>();
    private String queryPrefix;

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
    void prepareFixtures() {
        queryPrefix = "ADM066-" + UUID.randomUUID().toString().replace("-", "");
    }

    @AfterEach
    void removeFixtures() {
        for (UUID userId : fixtureUsers) {
            jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);
        }
        fixtureUsers.clear();
    }

    @Test
    void returnsDistinctLiveUniversitiesInFrequencyOrder() {
        String popular = queryPrefix + "-popular";
        String lessCommon = queryPrefix + "-less-common";
        String deleted = queryPrefix + "-deleted";
        insertUser(popular, "ACTIVE", false);
        insertUser(popular, "ACTIVE", false);
        insertUser(popular, "ACTIVE", false);
        insertUser(lessCommon, "ACTIVE", false);
        insertUser(deleted, "DELETED", true);
        insertUser(deleted, "DELETED", true);

        assertThat(repository.findUniversities(queryPrefix))
            .containsExactly(popular, lessCommon);
    }

    @Test
    void capsFilteredResultsAtFifty() {
        for (int index = 0; index < 51; index++) {
            insertUser(queryPrefix + "-" + String.format("%02d", index), "ACTIVE", false);
        }

        assertThat(repository.findUniversities(queryPrefix)).hasSize(50);
    }

    @Test
    void emptyQueryAlsoReturnsAtMostFiftyValues() {
        assertThat(repository.findUniversities("")).hasSizeLessThanOrEqualTo(50);
    }

    private void insertUser(String university, String accountStatus, boolean deleted) {
        UUID userId = UUID.randomUUID();
        String suffix = userId.toString().replace("-", "");
        jdbcTemplate.update("""
            INSERT INTO users (
                user_id, full_name, username, email, university_name, account_status,
                created_at, deleted_at
            ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
            """,
            userId,
            "ADM-066 fixture",
            "adm066_" + suffix,
            "adm066_" + suffix + "@example.com",
            university,
            accountStatus,
            deleted ? new java.sql.Timestamp(System.currentTimeMillis()) : null);
        fixtureUsers.add(userId);
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
