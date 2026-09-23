package kh.edu.acleda.campuslink.modules.admin.analytics.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** PostgreSQL regression coverage for all six ADM-065 activity counters. */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "GENLINK_TEST_POSTGRES_URL", matches = ".+")
class AdminUserActivityQueryRepositoryPostgresTest {

    @Autowired
    private AdminUserActivityQueryRepository queryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> fixtureUsers = new ArrayList<>();
    private final List<UUID> fixtureCommunities = new ArrayList<>();
    private final List<UUID> fixtureMemberships = new ArrayList<>();
    private final List<UUID> fixturePosts = new ArrayList<>();
    private final List<UUID> fixtureComments = new ArrayList<>();
    private final List<UUID> fixtureDiscussions = new ArrayList<>();
    private final List<UUID> fixtureForumComments = new ArrayList<>();
    private final List<UUID> fixtureEvents = new ArrayList<>();
    private final List<UUID> fixtureRsvps = new ArrayList<>();
    private final List<UUID> fixturePostReports = new ArrayList<>();
    private final List<UUID> fixtureDiscussionReports = new ArrayList<>();

    private UUID subjectUser;
    private UUID reporterUser;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnv("GENLINK_TEST_POSTGRES_URL"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username",
            () -> envOrDefault("GENLINK_TEST_POSTGRES_USERNAME", "postgres"));
        registry.add("spring.datasource.password",
            () -> envOrDefault("GENLINK_TEST_POSTGRES_PASSWORD", "postgres"));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.change-log",
            () -> "classpath:db/changelog/db.changelog-master.yaml");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @BeforeEach
    void seedActivityFixtures() {
        subjectUser = insertUser("subject");
        reporterUser = insertUser("reporter");

        UUID activeCommunity = insertCommunity("active", subjectUser, null);
        UUID deletedCommunity = insertCommunity("deleted", subjectUser, "2026-09-01 00:00:00");
        insertMembership(activeCommunity, subjectUser, "ACTIVE");
        insertMembership(activeCommunity, reporterUser, "LEFT");
        insertMembership(deletedCommunity, subjectUser, "ACTIVE");

        UUID activePost = insertPost(subjectUser, null);
        UUID deletedPost = insertPost(subjectUser, "2026-09-01 00:00:00");
        UUID reporterPost = insertPost(reporterUser, null);
        insertPostComment(activePost, subjectUser, null);
        insertPostComment(deletedPost, subjectUser, null);
        insertPostComment(activePost, reporterUser, "2026-09-01 00:00:00");

        UUID activeDiscussion = insertDiscussion(subjectUser, null);
        UUID deletedDiscussion = insertDiscussion(subjectUser, "2026-09-01 00:00:00");
        UUID reporterDiscussion = insertDiscussion(reporterUser, null);
        insertForumComment(activeDiscussion, subjectUser, null);
        insertForumComment(deletedDiscussion, subjectUser, null);
        insertForumComment(activeDiscussion, reporterUser, "2026-09-01 00:00:00");

        UUID activeEvent = insertEvent(reporterUser, null);
        UUID attendedEvent = insertEvent(reporterUser, null);
        UUID deletedEvent = insertEvent(reporterUser, "2026-09-01 00:00:00");
        insertRsvp(activeEvent, subjectUser, "JOINED");
        insertRsvp(attendedEvent, subjectUser, "ATTENDED");
        insertRsvp(activeEvent, reporterUser, "CANCELLED");
        insertRsvp(deletedEvent, subjectUser, "JOINED");

        insertPostReport(reporterPost, subjectUser);
        insertPostReport(deletedPost, subjectUser);
        insertDiscussionReport(reporterDiscussion, subjectUser);
        insertDiscussionReport(deletedDiscussion, subjectUser);
        insertPostReport(activePost, reporterUser);
        insertDiscussionReport(activeDiscussion, reporterUser);
        insertPostReport(deletedPost, reporterUser);
    }

    @AfterEach
    void removeActivityFixtures() {
        deleteRows("post_reports", "post_report_id", fixturePostReports);
        deleteRows("discussion_reports", "discussion_report_id", fixtureDiscussionReports);
        deleteRows("event_rsvps", "rsvp_id", fixtureRsvps);
        deleteRows("post_comments", "comment_id", fixtureComments);
        deleteRows("forum_comments", "forum_comment_id", fixtureForumComments);
        deleteRows("community_members", "community_member_id", fixtureMemberships);
        deleteRows("posts", "post_id", fixturePosts);
        deleteRows("forum_discussions", "discussion_id", fixtureDiscussions);
        deleteRows("events", "event_id", fixtureEvents);
        deleteRows("communities", "community_id", fixtureCommunities);
        deleteRows("users", "user_id", fixtureUsers);
        fixtureUsers.clear();
        fixtureCommunities.clear();
        fixtureMemberships.clear();
        fixturePosts.clear();
        fixtureComments.clear();
        fixtureDiscussions.clear();
        fixtureForumComments.clear();
        fixtureEvents.clear();
        fixtureRsvps.clear();
        fixturePostReports.clear();
        fixtureDiscussionReports.clear();
    }

    @Test
    void countsOnlyLiveContentAndActiveMemberships() {
        AdminUserActivityQueryRepository.UserActivityCounts counts =
            queryRepository.findByUserId(subjectUser);

        assertThat(counts).isEqualTo(new AdminUserActivityQueryRepository.UserActivityCounts(
            true, 1, 2, 1, 2, 2, 2));
    }

    @Test
    void distinguishesAnUnknownUserFromARealUserWithNoActivity() {
        UUID quietUser = insertUser("quiet");
        AdminUserActivityQueryRepository.UserActivityCounts quietCounts =
            queryRepository.findByUserId(quietUser);
        AdminUserActivityQueryRepository.UserActivityCounts unknownCounts =
            queryRepository.findByUserId(UUID.randomUUID());

        assertThat(quietCounts.userExists()).isTrue();
        assertThat(quietCounts.posts()).isZero();
        assertThat(quietCounts.comments()).isZero();
        assertThat(unknownCounts.userExists()).isFalse();
    }

    private UUID insertUser(String label) {
        UUID userId = UUID.randomUUID();
        String suffix = userId.toString().replace("-", "");
        jdbcTemplate.update("""
            INSERT INTO users (
                user_id, full_name, username, email, account_status, created_at
            ) VALUES (?, ?, ?, ?, 'ACTIVE', CAST('2026-01-01 00:00:00' AS TIMESTAMP))
            """,
            userId,
            "ADM-065 " + label,
            "adm065_" + suffix,
            "adm065_" + suffix + "@example.com");
        fixtureUsers.add(userId);
        return userId;
    }

    private UUID insertCommunity(String label, UUID creatorId, String deletedAt) {
        UUID communityId = UUID.randomUUID();
        String slug = "adm065_" + label + "_" + communityId.toString().replace("-", "");
        jdbcTemplate.update("""
            INSERT INTO communities (
                community_id, name, slug, privacy, created_by_user_id, deleted_at
            ) VALUES (?, ?, ?, 'PUBLIC', ?, CAST(? AS TIMESTAMP))
            """,
            communityId, "ADM-065 " + label, slug, creatorId, deletedAt);
        fixtureCommunities.add(communityId);
        return communityId;
    }

    private void insertMembership(UUID communityId, UUID userId, String status) {
        UUID membershipId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO community_members (
                community_member_id, community_id, user_id, role, status
            ) VALUES (?, ?, ?, 'MEMBER', ?)
            """, membershipId, communityId, userId, status);
        fixtureMemberships.add(membershipId);
    }

    private UUID insertPost(UUID userId, String deletedAt) {
        UUID postId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO posts (
                post_id, user_id, content_text, post_type, visibility, deleted_at
            ) VALUES (?, ?, 'ADM-065 post', 'TEXT', 'PUBLIC', CAST(? AS TIMESTAMP))
            """, postId, userId, deletedAt);
        fixturePosts.add(postId);
        return postId;
    }

    private void insertPostComment(UUID postId, UUID userId, String deletedAt) {
        UUID commentId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO post_comments (
                comment_id, post_id, user_id, content, deleted_at
            ) VALUES (?, ?, ?, 'ADM-065 comment', CAST(? AS TIMESTAMP))
            """, commentId, postId, userId, deletedAt);
        fixtureComments.add(commentId);
    }

    private UUID insertDiscussion(UUID userId, String deletedAt) {
        UUID discussionId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO forum_discussions (
                discussion_id, user_id, title, content, is_anonymous, deleted_at
            ) VALUES (?, ?, 'ADM-065 discussion', 'ADM-065 content', FALSE, CAST(? AS TIMESTAMP))
            """, discussionId, userId, deletedAt);
        fixtureDiscussions.add(discussionId);
        return discussionId;
    }

    private void insertForumComment(UUID discussionId, UUID userId, String deletedAt) {
        UUID commentId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO forum_comments (
                forum_comment_id, discussion_id, user_id, content, deleted_at
            ) VALUES (?, ?, ?, 'ADM-065 comment', CAST(? AS TIMESTAMP))
            """, commentId, discussionId, userId, deletedAt);
        fixtureForumComments.add(commentId);
    }

    private UUID insertEvent(UUID organizerId, String deletedAt) {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO events (
                event_id, organizer_id, title, date_time, end_date_time, event_mode,
                is_paid, status, deleted_at
            ) VALUES (
                ?, ?, 'ADM-065 event', CAST('2026-10-01 10:00:00' AS TIMESTAMP),
                CAST('2026-10-01 11:00:00' AS TIMESTAMP), 'IN_PERSON', FALSE,
                'PUBLISHED', CAST(? AS TIMESTAMP)
            )
            """, eventId, organizerId, deletedAt);
        fixtureEvents.add(eventId);
        return eventId;
    }

    private void insertRsvp(UUID eventId, UUID userId, String status) {
        UUID rsvpId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO event_rsvps (rsvp_id, event_id, user_id, status, checked_in)
            VALUES (?, ?, ?, ?, FALSE)
            """, rsvpId, eventId, userId, status);
        fixtureRsvps.add(rsvpId);
    }

    private void insertPostReport(UUID postId, UUID userId) {
        UUID reportId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO post_reports (post_report_id, post_id, user_id, reason)
            VALUES (?, ?, ?, 'ADM-065')
            """, reportId, postId, userId);
        fixturePostReports.add(reportId);
    }

    private void insertDiscussionReport(UUID discussionId, UUID userId) {
        UUID reportId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO discussion_reports (discussion_report_id, discussion_id, user_id, reason)
            VALUES (?, ?, ?, 'ADM-065')
            """, reportId, discussionId, userId);
        fixtureDiscussionReports.add(reportId);
    }

    private void deleteRows(String table, String column, List<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        jdbcTemplate.update(
            "DELETE FROM " + table + " WHERE " + column + " IN (" + placeholders + ")",
            ids.toArray());
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
