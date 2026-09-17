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
                .contains("{challengeName}")
                .contains("{rewardXp}")
                .contains("SECURITY_SUSPICIOUS_LOGIN")
                .contains("{loginLocation}")
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

    @Test
    void autoSaveTemplatesAreBilingualAndRerunSafe() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(
            "/db/changelog/changes/0056-seed-auto-save-notification-templates.sql")) {
            assertThat(stream).as("auto-save notification template migration").isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql)
                .contains("FINANCE_AUTO_SAVE_APPLIED")
                .contains("FINANCE_AUTO_SAVE_SKIPPED")
                .contains("{amount}")
                .contains("{currency}")
                .contains("{goalName}")
                .contains("ON CONFLICT (template_key) DO NOTHING")
                .contains("title_km")
                .contains("message_km");
        }
    }

    @Test
    void paymentReceiptTemplatesAreBilingualAndRerunSafe() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(
            "/db/changelog/changes/0057-seed-payment-receipt-notification-templates.sql")) {
            assertThat(stream).as("payment receipt notification template migration").isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql)
                .contains("COMMUNITY_EVENT_PAYMENT_RECEIPT")
                .contains("FINANCE_WALLET_PAYMENT_RECEIPT")
                .contains("{amount}")
                .contains("{currency}")
                .contains("{eventTitle}")
                .contains("{merchant}")
                .contains("{provider}")
                .contains("{providerRef}")
                .contains("ON CONFLICT (template_key) DO NOTHING")
                .contains("title_km")
                .contains("message_km");
        }
    }

    @Test
    void goalMilestoneTemplateIsBilingualAndRerunSafe() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(
            "/db/changelog/changes/0058-seed-goal-milestone-notification-template.sql")) {
            assertThat(stream).as("goal milestone notification template migration").isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql)
                .contains("PRODUCTIVITY_GOAL_MILESTONE")
                .contains("{milestoneTitle}")
                .contains("{goalTitle}")
                .contains("{progressPercent}")
                .contains("ON CONFLICT (template_key) DO NOTHING")
                .contains("title_km")
                .contains("message_km");
        }
    }

    @Test
    void chatAndSocialTemplatesAreBilingualAndRerunSafe() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(
            "/db/changelog/changes/0066-seed-chat-and-social-notification-templates.sql")) {
            assertThat(stream).as("chat and social notification template migration").isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql)
                .contains("CHAT_NEW_MESSAGE")
                .contains("COMMUNITY_FOLLOW_REQUEST")
                .contains("COMMUNITY_NEW_FOLLOWER")
                .contains("COMMUNITY_FOLLOW_ACCEPTED")
                .contains("COMMUNITY_POST_COMMENT")
                .contains("COMMUNITY_COMMENT_REPLY")
                .contains("COMMUNITY_POST_LIKE")
                .contains("{senderName}")
                .contains("{preview}")
                .contains("{followerName}")
                .contains("{userName}")
                .contains("{commenterName}")
                .contains("{commentSnippet}")
                .contains("{replierName}")
                .contains("{replySnippet}")
                .contains("{likerName}")
                .contains("ON CONFLICT (template_key) DO NOTHING")
                .contains("title_km")
                .contains("message_km");
        }
    }
}

