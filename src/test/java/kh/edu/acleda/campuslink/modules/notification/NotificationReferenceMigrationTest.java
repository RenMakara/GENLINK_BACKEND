package kh.edu.acleda.campuslink.modules.notification;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationReferenceMigrationTest {

    @Test
    void migrationAddsNavigationColumnsAndRecipientScopedDedupeIndex() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(
            "/db/changelog/changes/0053-add-notification-navigation-and-dedupe.sql")) {
            assertThat(stream).as("notification navigation migration").isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql)
                .contains("ADD COLUMN IF NOT EXISTS reference_type")
                .contains("ADD COLUMN IF NOT EXISTS reference_id")
                .contains("ADD COLUMN IF NOT EXISTS dedupe_key")
                .contains("ON notifications (reference_type, reference_id)")
                .contains("ON notifications (user_id, dedupe_key)")
                .contains("WHERE dedupe_key IS NOT NULL");
        }
    }
}
