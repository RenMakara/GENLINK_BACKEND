package kh.edu.acleda.campuslink.modules.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Static checks for the event details and full registration migration. */
class Migration0050ScriptTest {

    @Test
    void changelogRegisters0050ImmediatelyAfter0049() throws Exception {
        String master = read("db/changelog/db.changelog-master.yaml");
        int migration0049 = master.indexOf("0049-fix-budget-soft-delete-uniqueness.sql");
        int migration0050 = master.indexOf("0050-expand-event-details-and-registration.sql");

        assertThat(migration0049).isGreaterThanOrEqualTo(0);
        assertThat(migration0050).isGreaterThan(migration0049);
    }

    @Test
    void addsEventScheduleModeAndRegistrationFields() throws Exception {
        String script = read("db/changelog/changes/0050-expand-event-details-and-registration.sql");

        assertThat(script).contains(
            "end_date_time",
            "event_mode",
            "meeting_url",
            "full_name",
            "phone_number",
            "study_year",
            "terms_accepted",
            "chk_events_end_after_start",
            "chk_events_coordinates_pair");
    }

    private static String read(String path) throws Exception {
        return new String(new ClassPathResource(path).getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
    }
}
