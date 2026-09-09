package kh.edu.acleda.campuslink.modules.notification;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplateSeedTest {

    @Test
    void seedContainsTheInitialBilingualTemplateKeysAndIsRerunSafe() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(
            "/db/changelog/changes/0052-seed-notification-templates.sql")) {
            assertThat(stream).as("notification template seed migration").isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql)
                .contains("COMMUNITY_EVENT_RSVP_ORGANIZER")
                .contains("FINANCE_BUDGET_APPROACHING")
                .contains("PRODUCTIVITY_TASK_DUE")
                .contains("ENTERTAINMENT_CHALLENGE_COMPLETE")
                .contains("SECURITY_SUSPICIOUS_LOGIN")
                .contains("ON CONFLICT (template_key) DO NOTHING")
                .contains("title_km")
                .contains("message_km");
        }
    }

    @Test
    void forumModerationTemplateIsBilingualAndRerunSafe() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(
            "/db/changelog/changes/0054-seed-forum-notification-template.sql")) {
            assertThat(stream).as("forum moderation notification template migration").isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql)
                .contains("FORUM_DISCUSSION_MODERATED")
                .contains("{discussionTitle}")
                .contains("{moderationAction}")
                .contains("ON CONFLICT (template_key) DO NOTHING")
                .contains("title_km")
                .contains("message_km");
        }
    }

    @Test
    void eventReminderTemplateIsBilingualAndRerunSafe() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(
            "/db/changelog/changes/0055-seed-event-reminder-template.sql")) {
            assertThat(stream).as("event reminder notification template migration").isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql)
                .contains("COMMUNITY_EVENT_REMINDER")
                .contains("{eventTitle}")
                .contains("{startsAt}")
                .contains("ON CONFLICT (template_key) DO NOTHING")
                .contains("title_km")
                .contains("message_km");
        }
    }
}
