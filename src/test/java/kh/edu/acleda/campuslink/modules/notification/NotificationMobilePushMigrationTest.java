package kh.edu.acleda.campuslink.modules.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NotificationMobilePushMigrationTest {

    @Test
    void registersMobilePushMigrationAfterChannelPreferences() throws IOException {
        String master = read("/db/changelog/db.changelog-master.yaml");
        assertThat(master.indexOf("0063-add-mobile-push-devices.sql"))
            .isGreaterThan(master.indexOf("0062-create-notification-channel-preferences.sql"));

        String sql = read("/db/changelog/changes/0063-add-mobile-push-devices.sql");
        assertThat(sql)
            .contains("MOBILE_PUSH")
            .contains("CREATE TABLE IF NOT EXISTS notification_devices")
            .contains("token_hash")
            .contains("token_ciphertext")
            .contains("UNIQUE (token_hash)")
            .contains("platform IN ('ANDROID', 'IOS')");
    }

    private String read(String resource) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            assertThat(stream).as("resource %s", resource).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
