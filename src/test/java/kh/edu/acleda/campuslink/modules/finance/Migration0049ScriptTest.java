package kh.edu.acleda.campuslink.modules.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Static checks for the soft-delete-aware budget uniqueness migration. */
class Migration0049ScriptTest {

    @Test
    void changelogRegisters0049ImmediatelyAfter0048() throws Exception {
        String master = read("db/changelog/db.changelog-master.yaml");
        int migration0048 = master.indexOf("0048-finance-wallet-consistency.sql");
        int migration0049 = master.indexOf("0049-fix-budget-soft-delete-uniqueness.sql");

        assertThat(migration0048).isGreaterThanOrEqualTo(0);
        assertThat(migration0049).isGreaterThan(migration0048);
    }

    @Test
    void replacesTheFullConstraintWithAnActiveRowsOnlyIndex() throws Exception {
        String script = read("db/changelog/changes/0049-fix-budget-soft-delete-uniqueness.sql");

        assertThat(script).contains(
            "DROP CONSTRAINT IF EXISTS uk_budgets_user_month_year_currency");
        assertThat(script).contains(
            "CREATE UNIQUE INDEX IF NOT EXISTS uk_budgets_active_user_month_year_currency");
        assertThat(script).contains("WHERE deleted_at IS NULL");
    }

    private static String read(String path) throws Exception {
        return new String(new ClassPathResource(path).getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
    }
}
