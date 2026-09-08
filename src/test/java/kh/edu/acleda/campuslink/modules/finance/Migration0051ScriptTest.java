package kh.edu.acleda.campuslink.modules.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Static checks for the event currency migration. */
class Migration0051ScriptTest {

    @Test
    void changelogRegisters0051After0050() throws Exception {
        String master = read("db/changelog/db.changelog-master.yaml");
        int migration0050 = master.indexOf("0050-expand-event-details-and-registration.sql");
        int migration0051 = master.indexOf("0051-add-event-currency.sql");

        assertThat(migration0050).isGreaterThanOrEqualTo(0);
        assertThat(migration0051).isGreaterThan(migration0050);
    }

    @Test
    void addsCurrencyAndProtectsPaidEvents() throws Exception {
        String script = read("db/changelog/changes/0051-add-event-currency.sql");

        assertThat(script).contains(
            "currency VARCHAR(3)",
            "SET currency = 'USD'",
            "chk_events_currency_value",
            "chk_events_paid_requires_currency",
            "'USD', 'KHR'");
    }

    private static String read(String path) throws Exception {
        return new String(new ClassPathResource(path).getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
    }
}
