package kh.edu.acleda.campuslink.modules.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Executes the real 0048 SQL against an isolated PostgreSQL schema. */
@JdbcTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.NONE)
@EnabledIfEnvironmentVariable(named = "GENLINK_TEST_POSTGRES_URL", matches = ".+")
class Migration0048PostgresVerificationTest {

    @Autowired private DataSource dataSource;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnv("GENLINK_TEST_POSTGRES_URL"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", () -> envOrDefault("GENLINK_TEST_POSTGRES_USERNAME", "postgres"));
        registry.add("spring.datasource.password", () -> envOrDefault("GENLINK_TEST_POSTGRES_PASSWORD", "postgres"));
        registry.add("spring.liquibase.enabled", () -> "false");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Test
    void backfillsOnlySuccessfulExpenseDebitsAndCreatesIndex() throws Exception {
        String schema = "migration_0048_" + UUID.randomUUID().toString().replace("-", "");
        UUID matched = UUID.randomUUID();
        UUID failed = UUID.randomUUID();
        UUID credit = UUID.randomUUID();
        UUID unrelated = UUID.randomUUID();
        try (var connection = dataSource.getConnection()) {
            connection.createStatement().execute("CREATE SCHEMA " + schema);
            connection.createStatement().execute("SET search_path TO " + schema);
            connection.createStatement().execute("CREATE TABLE expenses (expense_id UUID PRIMARY KEY)");
            connection.createStatement().execute("CREATE TABLE demo_transactions ("
                + "transaction_id UUID PRIMARY KEY, related_type VARCHAR(40), related_id UUID, "
                + "direction VARCHAR(10), status VARCHAR(50))");
            connection.createStatement().execute("CREATE TABLE saving_goals ("
                + "saving_goal_id UUID PRIMARY KEY, user_id UUID, currency VARCHAR(3), "
                + "status VARCHAR(50), deleted_at TIMESTAMP)");

            for (UUID expenseId : new UUID[]{matched, failed, credit, unrelated}) {
                try (var statement = connection.prepareStatement("INSERT INTO expenses(expense_id) VALUES (?)")) {
                    statement.setObject(1, expenseId);
                    statement.executeUpdate();
                }
            }
            insertLedgerRow(connection, matched, "EXPENSE", "DEBIT", "SUCCESS");
            insertLedgerRow(connection, failed, "EXPENSE", "DEBIT", "FAILED");
            insertLedgerRow(connection, credit, "EXPENSE", "CREDIT", "SUCCESS");
            insertLedgerRow(connection, unrelated, "OTHER", "DEBIT", "SUCCESS");

            ScriptUtils.executeSqlScript(connection,
                new ClassPathResource("db/changelog/changes/0048-finance-wallet-consistency.sql"));

            try (var query = connection.prepareStatement(
                "SELECT expense_id, paid_from_wallet FROM expenses ORDER BY expense_id")) {
                try (var rows = query.executeQuery()) {
                    int trueCount = 0;
                    while (rows.next()) {
                        if (rows.getBoolean("paid_from_wallet")) {
                            trueCount++;
                            assertThat(rows.getObject("expense_id", UUID.class)).isEqualTo(matched);
                        }
                    }
                    assertThat(trueCount).isEqualTo(1);
                }
            }

            try (var indexQuery = connection.prepareStatement(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = ? "
                    + "AND indexname = 'idx_saving_goals_user_currency_status'")) {
                indexQuery.setString(1, schema);
                try (var rows = indexQuery.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(1);
                }
            }
        } finally {
            try (var cleanup = dataSource.getConnection()) {
                cleanup.createStatement().execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    @Test
    void changelogRegisters0048ImmediatelyAfter0047() throws Exception {
        String master = new String(new ClassPathResource("db/changelog/db.changelog-master.yaml")
            .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(master.indexOf("0047-fix-domain-correctness-constraints.sql"))
            .isLessThan(master.indexOf("0048-finance-wallet-consistency.sql"));
    }

    private void insertLedgerRow(java.sql.Connection connection, UUID expenseId,
                                 String relatedType, String direction, String status) throws Exception {
        try (var statement = connection.prepareStatement(
            "INSERT INTO demo_transactions(transaction_id, related_type, related_id, direction, status) "
                + "VALUES (?, ?, ?, ?, ?)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, relatedType);
            statement.setObject(3, expenseId);
            statement.setString(4, direction);
            statement.setString(5, status);
            statement.executeUpdate();
        }
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
