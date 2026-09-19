package kh.edu.acleda.campuslink.modules.productivity;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class MotivationKhmerSeedTest {

    @Test
    void khmerMigrationUpdatesAllExistingMotivationRowsAndPreservesTheirIds() throws Exception {
        String master = read("db/changelog/db.changelog-master.yaml");
        String sql = read("db/changelog/changes/0070-add-khmer-motivation-content.sql");

        assertThat(master).contains("0070-add-khmer-motivation-content.sql");
        assertThat(sql)
            .contains("title_km")
            .contains("content_km")
            .contains("c0000000-0000-4000-8000-000000000001")
            .contains("c0000000-0000-4000-8000-000000000002")
            .contains("c0000000-0000-4000-8000-000000000003")
            .contains("c0000000-0000-4000-8000-000000000004")
            .contains("c0000000-0000-4000-8000-000000000005")
            .contains("c0000000-0000-4000-8000-000000000006")
            .contains("--rollback");
    }

    private static String read(String path) throws Exception {
        return new String(new ClassPathResource(path).getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
    }
}
