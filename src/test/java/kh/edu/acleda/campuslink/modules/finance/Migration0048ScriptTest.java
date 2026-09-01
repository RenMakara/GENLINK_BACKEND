package kh.edu.acleda.campuslink.modules.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Static checks that remain runnable without a PostgreSQL instance. */
class Migration0048ScriptTest {

    @Test
    void changelogRegisters0048ImmediatelyAfter0047() throws Exception {
        String master = read("db/changelog/db.changelog-master.yaml");
        int migration0047 = master.indexOf("0047-fix-domain-correctness-constraints.sql");
        int migration0048 = master.indexOf("0048-finance-wallet-consistency.sql");

        assertThat(migration0047).isGreaterThanOrEqualTo(0);
        assertThat(migration0048).isGreaterThan(migration0047);
    }

    @Test
    void backfillIsNarrowAndIndexIsIdempotent() throws Exception {
        String script = read("db/changelog/changes/0048-finance-wallet-consistency.sql");

        assertThat(script).contains("ADD COLUMN IF NOT EXISTS paid_from_wallet BOOLEAN NOT NULL DEFAULT FALSE");
        assertThat(script).contains("t.related_type = 'EXPENSE'");
        assertThat(script).contains("t.direction = 'DEBIT'");
        assertThat(script).contains("t.status = 'SUCCESS'");
        assertThat(script).contains("CREATE INDEX IF NOT EXISTS idx_saving_goals_user_currency_status");
    }

    private static String read(String path) throws Exception {
        return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
