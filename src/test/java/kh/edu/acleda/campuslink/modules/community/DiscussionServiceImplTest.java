package kh.edu.acleda.campuslink.modules.community;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.CommentCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.DiscussionCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.DiscussionReportRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.CommentResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.DiscussionResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.ReportedDiscussionResponse;
import kh.edu.acleda.campuslink.modules.community.entity.DiscussionReport;
import kh.edu.acleda.campuslink.modules.community.entity.ForumComment;
import kh.edu.acleda.campuslink.modules.community.entity.ForumCommentReaction;
import kh.edu.acleda.campuslink.modules.community.entity.ForumDiscussion;
import kh.edu.acleda.campuslink.modules.community.entity.ForumReaction;
import kh.edu.acleda.campuslink.modules.community.repository.DiscussionReportRepository;
import kh.edu.acleda.campuslink.modules.community.repository.ForumCommentReactionRepository;
import kh.edu.acleda.campuslink.modules.community.repository.ForumCommentRepository;
import kh.edu.acleda.campuslink.modules.community.repository.ForumDiscussionRepository;
import kh.edu.acleda.campuslink.modules.community.repository.ForumReactionRepository;
import kh.edu.acleda.campuslink.modules.community.service.impl.DiscussionServiceImpl;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiscussionServiceImplTest {

    private static final UUID AUTHOR_ID = UUID.randomUUID();
    private static final UUID OTHER_ID = UUID.randomUUID();
    private static final UUID DISCUSSION_ID = UUID.randomUUID();

    @Mock private ForumDiscussionRepository discussionRepository;
    @Mock private ForumCommentRepository commentRepository;
    @Mock private ForumReactionRepository reactionRepository;
    @Mock private ForumCommentReactionRepository commentReactionRepository;
    @Mock private DiscussionReportRepository reportRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private NotificationEmitter notificationEmitter;

    private DiscussionServiceImpl service;
    private User author;
    private ForumDiscussion discussion;

    @BeforeEach
    void setUp() {
        service = new DiscussionServiceImpl(discussionRepository, commentRepository, reactionRepository,
            commentReactionRepository, reportRepository, userRepository, currentUserProvider, notificationEmitter);

        author = new User();
        author.setEmail("author@example.com");
        author.setFullName("Sok Dara");
        author.setUsername("sokdara");
        ReflectionTestUtils.setField(author, "id", AUTHOR_ID);

        discussion = ForumDiscussion.builder().user(author).title("How do you stay focused?")
            .content("Share tips").category("EDUCATION").isAnonymous(false)
            .reactionCount(4).commentCount(2).reportCount(0).build();
        ReflectionTestUtils.setField(discussion, "id", DISCUSSION_ID);

        when(currentUserProvider.getCurrentUserId()).thenReturn(AUTHOR_ID);
        when(userRepository.findById(AUTHOR_ID)).thenReturn(Optional.of(author));
        when(userRepository.findById(OTHER_ID)).thenReturn(Optional.of(author));
        when(discussionRepository.findByIdAndDeletedAtIsNull(DISCUSSION_ID)).thenReturn(Optional.of(discussion));
        when(discussionRepository.save(any(ForumDiscussion.class))).thenAnswer(i -> {
            ForumDiscussion d = i.getArgument(0);
            if (d.getId() == null) {
                ReflectionTestUtils.setField(d, "id", DISCUSSION_ID);
            }
            return d;
        });
        when(reactionRepository.findByDiscussionIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(reactionRepository.findReactedDiscussionIds(any(), anyCollection())).thenReturn(List.of());
        when(commentRepository.save(any(ForumComment.class))).thenAnswer(i -> i.getArgument(0));
        when(commentReactionRepository.findReactedCommentIds(any(), anyCollection())).thenReturn(List.of());
    }

    /** The stub echoed the request back with a null id and saved nothing. */
    @Test
    void createPersistsTheDiscussion() {
        DiscussionResponse response = service.create(new DiscussionCreateRequest(
            "Title", "Content", "TECH", false, Language.ENGLISH));

        verify(discussionRepository).save(any(ForumDiscussion.class));
        assertThat(response.author().username()).isEqualTo("sokdara");
        assertThat(response.reactionCount()).isZero();
    }

    /**
     * The one that matters: an anonymous discussion must not carry its author to
     * anyone. This DTO boundary is the only thing enforcing it.
     */
    @Test
    void anonymousDiscussionHidesItsAuthorFromOtherUsers() {
        discussion.setIsAnonymous(true);
        when(currentUserProvider.getCurrentUserId()).thenReturn(OTHER_ID);

        DiscussionResponse response = service.getById(DISCUSSION_ID);

        assertThat(response.anonymous()).isTrue();
        assertThat(response.author()).isNull();
        assertThat(response.mine()).isFalse();
        // Belt and braces: the name must not survive anywhere in the payload.
        assertThat(response.toString()).doesNotContain("Sok Dara").doesNotContain("sokdara");
    }

    /** The author is stored, not discarded: moderation needs to know who wrote it. */
    @Test
    void anonymousDiscussionStillStoresTheAuthorOnTheRow() {
        service.create(new DiscussionCreateRequest("Anon", "Content", "TECH", true, Language.ENGLISH));

        var captor = org.mockito.ArgumentCaptor.forClass(ForumDiscussion.class);
        verify(discussionRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(author);
        assertThat(captor.getValue().getIsAnonymous()).isTrue();
    }

    /** mine tells the writer what they already know, and stays false for everyone else. */
    @Test
    void anonymousDiscussionIsStillFlaggedMineToItsAuthor() {
        discussion.setIsAnonymous(true);

        DiscussionResponse response = service.getById(DISCUSSION_ID);

        assertThat(response.mine()).isTrue();
        assertThat(response.author()).isNull();
    }

    @Test
    void nonAnonymousDiscussionShowsItsAuthor() {
        DiscussionResponse response = service.getById(DISCUSSION_ID);

        assertThat(response.anonymous()).isFalse();
        assertThat(response.author().fullName()).isEqualTo("Sok Dara");
    }

    @Test
    void browseHidesAuthorsOfAnonymousRows() {
        discussion.setIsAnonymous(true);
        when(discussionRepository.findFeed(any(), any())).thenReturn(new PageImpl<>(List.of(discussion)));
        when(currentUserProvider.getCurrentUserId()).thenReturn(OTHER_ID);

        PageResponse<DiscussionResponse> page = service.browse(null, PageRequest.of(0, 20));

        assertThat(page.content().get(0).author()).isNull();
    }

    @Test
    void reactIsIdempotent() {
        when(reactionRepository.findByDiscussionIdAndUserId(DISCUSSION_ID, AUTHOR_ID))
            .thenReturn(Optional.of(ForumReaction.builder().build()));

        assertThat(service.react(DISCUSSION_ID)).isEqualTo(4);
        verify(reactionRepository, never()).save(any(ForumReaction.class));
        verify(discussionRepository, never()).adjustReactionCount(any(), anyInt());
    }

    @Test
    void reactCreatesTheReactionAndIncrementsTheCount() {
        assertThat(service.react(DISCUSSION_ID)).isEqualTo(5);
        verify(discussionRepository).adjustReactionCount(DISCUSSION_ID, 1);
    }

    @Test
    void unreactingSomethingNeverReactedToIsANoOp() {
        assertThat(service.unreact(DISCUSSION_ID)).isEqualTo(4);
        verify(discussionRepository, never()).adjustReactionCount(any(), anyInt());
    }

    /** Otherwise report_count counts taps, not people. */
    @Test
    void reportingTwiceCountsOnce() {
        when(reportRepository.existsByDiscussionIdAndUserId(DISCUSSION_ID, AUTHOR_ID)).thenReturn(true);

        service.report(DISCUSSION_ID, new DiscussionReportRequest("SPAM", null));

        verify(reportRepository, never()).save(any(DiscussionReport.class));
        verify(discussionRepository, never()).incrementReportCount(any());
    }

    @Test
    void reportRecordsTheReporterAndBumpsTheCount() {
        when(reportRepository.existsByDiscussionIdAndUserId(DISCUSSION_ID, AUTHOR_ID)).thenReturn(false);

        service.report(DISCUSSION_ID, new DiscussionReportRequest("SPAM", "buying followers"));

        var captor = org.mockito.ArgumentCaptor.forClass(DiscussionReport.class);
        verify(reportRepository).save(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo("SPAM");
        assertThat(captor.getValue().getUser()).isEqualTo(author);
        verify(discussionRepository).incrementReportCount(DISCUSSION_ID);
    }

    @Test
    void deleteIsASoftDelete() {
        when(discussionRepository.findByIdAndUserIdAndDeletedAtIsNull(DISCUSSION_ID, AUTHOR_ID))
            .thenReturn(Optional.of(discussion));

        service.delete(DISCUSSION_ID);

        assertThat(discussion.getDeletedAt()).isNotNull();
        verify(notificationEmitter, never()).emit(any(NotificationRequest.class));
    }

    @Test
    void deletingSomeoneElsesDiscussionReadsAsNotFound() {
        when(discussionRepository.findByIdAndUserIdAndDeletedAtIsNull(DISCUSSION_ID, AUTHOR_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(DISCUSSION_ID)).isInstanceOf(NotFoundException.class);
    }

    // ---- Admin moderation ----

    /**
     * The whole reason the author is stored on an anonymous row: the moderation queue must
     * show the admin who wrote it, even though the public sees it as anonymous.
     */
    @Test
    void theModerationQueueRevealsTheAuthorEvenForAnonymousDiscussions() {
        discussion.setIsAnonymous(true);
        discussion.setReportCount(3);
        when(discussionRepository.findReportedForModeration(any())).thenReturn(new PageImpl<>(List.of(discussion)));

        PageResponse<ReportedDiscussionResponse> queue = service.reportedDiscussions(PageRequest.of(0, 20));

        ReportedDiscussionResponse row = queue.content().get(0);
        assertThat(row.postedAnonymously()).isTrue();
        assertThat(row.author().username()).isEqualTo("sokdara");
        assertThat(row.reportCount()).isEqualTo(3);
    }

    @Test
    void adminRemovalSoftDeletesAnyDiscussion() {
        service.removeAsAdmin(DISCUSSION_ID);

        assertThat(discussion.getDeletedAt()).isNotNull();
        verify(discussionRepository).save(discussion);
    }

    @Test
    void adminRemovalNotifiesTheDiscussionAuthorWithReferenceAndDedupeData() {
        service.removeAsAdmin(DISCUSSION_ID);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationEmitter).emit(captor.capture());
        NotificationRequest request = captor.getValue();
        assertThat(request.recipientUserId()).isEqualTo(AUTHOR_ID);
        assertThat(request.templateKey()).isEqualTo("FORUM_DISCUSSION_MODERATED");
        assertThat(request.type()).isEqualTo("FORUM_DISCUSSION_MODERATED");
        assertThat(request.parameters())
            .containsEntry("discussionTitle", "How do you stay focused?")
            .containsEntry("moderationAction", "removed");
        assertThat(request.referenceType()).isEqualTo("DISCUSSION");
        assertThat(request.referenceId()).isEqualTo(DISCUSSION_ID);
        assertThat(request.dedupeKey())
            .isEqualTo("forum-discussion-moderated:" + DISCUSSION_ID + ":" + AUTHOR_ID);
    }

    @Test
    void adminRemovalOfAMissingDiscussionIsNotFound() {
        when(discussionRepository.findByIdAndDeletedAtIsNull(DISCUSSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeAsAdmin(DISCUSSION_ID)).isInstanceOf(NotFoundException.class);
        verify(notificationEmitter, never()).emit(any(NotificationRequest.class));
    }

    @Test
    void dismissingReportsClearsTheRowsAndZeroesTheCount() {
        service.dismissReports(DISCUSSION_ID);

        verify(reportRepository).deleteByDiscussionId(DISCUSSION_ID);
        verify(discussionRepository).clearReportCount(DISCUSSION_ID);
        verify(notificationEmitter, never()).emit(any(NotificationRequest.class));
    }

    // ---- Comments & replies ----

    @Test
    void addingATopLevelCommentHasNoParentAndBumpsTheCount() {
        service.addComment(DISCUSSION_ID, new CommentCreateRequest("Nice thread", null, Language.ENGLISH));

        var captor = org.mockito.ArgumentCaptor.forClass(ForumComment.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getParentComment()).isNull();
        verify(discussionRepository).adjustCommentCount(DISCUSSION_ID, 1);
    }

    @Test
    void replyingToACommentSetsItAsTheParent() {
        UUID parentId = UUID.randomUUID();
        ForumComment parent = ForumComment.builder().discussion(discussion).user(author).content("q").build();
        ReflectionTestUtils.setField(parent, "id", parentId);
        when(commentRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parent));

        service.addComment(DISCUSSION_ID, new CommentCreateRequest("an answer", parentId, Language.ENGLISH));

        var captor = org.mockito.ArgumentCaptor.forClass(ForumComment.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getParentComment()).isEqualTo(parent);
    }

    /** Threading is capped at one level: a reply to a reply attaches to the top-level parent. */
    @Test
    void replyingToAReplyCollapsesToTheTopLevelParent() {
        UUID topId = UUID.randomUUID();
        UUID replyId = UUID.randomUUID();
        ForumComment top = ForumComment.builder().discussion(discussion).user(author).content("top").build();
        ReflectionTestUtils.setField(top, "id", topId);
        ForumComment reply = ForumComment.builder().discussion(discussion).user(author)
            .content("reply").parentComment(top).build();
        ReflectionTestUtils.setField(reply, "id", replyId);
        when(commentRepository.findByIdAndDeletedAtIsNull(replyId)).thenReturn(Optional.of(reply));

        service.addComment(DISCUSSION_ID, new CommentCreateRequest("nested", replyId, Language.ENGLISH));

        var captor = org.mockito.ArgumentCaptor.forClass(ForumComment.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getParentComment()).isEqualTo(top);
    }

    /** A parent from another discussion must be rejected, or a reply could graft onto an unrelated thread. */
    @Test
    void replyingToACommentFromAnotherDiscussionIsNotFound() {
        UUID parentId = UUID.randomUUID();
        ForumDiscussion other = ForumDiscussion.builder().build();
        ReflectionTestUtils.setField(other, "id", OTHER_ID);
        ForumComment parent = ForumComment.builder().discussion(other).user(author).content("q").build();
        ReflectionTestUtils.setField(parent, "id", parentId);
        when(commentRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.addComment(DISCUSSION_ID,
            new CommentCreateRequest("x", parentId, Language.ENGLISH)))
            .isInstanceOf(NotFoundException.class);
        verify(commentRepository, never()).save(any(ForumComment.class));
    }

    @Test
    void theCommentListReturnsTopLevelOnly() {
        ForumComment top = ForumComment.builder().discussion(discussion).user(author).content("top").build();
        ReflectionTestUtils.setField(top, "id", UUID.randomUUID());
        when(commentRepository.findTopLevel(eq(DISCUSSION_ID), any())).thenReturn(new PageImpl<>(List.of(top)));

        PageResponse<CommentResponse> page = service.comments(DISCUSSION_ID, PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(1);
        verify(commentRepository).findTopLevel(eq(DISCUSSION_ID), any());
    }

    @Test
    void repliesReturnTheChildrenOfAComment() {
        UUID parentId = UUID.randomUUID();
        ForumComment reply = ForumComment.builder().discussion(discussion).user(author).content("r").build();
        ReflectionTestUtils.setField(reply, "id", UUID.randomUUID());
        when(commentRepository.findReplies(eq(parentId), any())).thenReturn(new PageImpl<>(List.of(reply)));

        PageResponse<CommentResponse> page = service.replies(parentId, PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(1);
        verify(commentRepository).findReplies(eq(parentId), any());
    }

    // ---- Comment likes ----

    @Test
    void likingACommentIsIdempotent() {
        ForumComment comment = likableComment(2);
        UUID commentId = comment.getId();
        when(commentReactionRepository.findByCommentIdAndUserId(commentId, AUTHOR_ID))
            .thenReturn(Optional.of(ForumCommentReaction.builder().build()));

        assertThat(service.likeComment(commentId)).isEqualTo(2);
        verify(commentReactionRepository, never()).save(any(ForumCommentReaction.class));
        verify(commentRepository, never()).adjustLikeCount(any(), anyInt());
    }

    @Test
    void likingACommentCreatesTheReactionAndIncrementsTheCount() {
        ForumComment comment = likableComment(2);
        UUID commentId = comment.getId();
        when(commentReactionRepository.findByCommentIdAndUserId(commentId, AUTHOR_ID)).thenReturn(Optional.empty());

        assertThat(service.likeComment(commentId)).isEqualTo(3);
        verify(commentRepository).adjustLikeCount(commentId, 1);
    }

    @Test
    void unlikingACommentNeverLikedIsANoOp() {
        ForumComment comment = likableComment(2);
        UUID commentId = comment.getId();
        when(commentReactionRepository.findByCommentIdAndUserId(commentId, AUTHOR_ID)).thenReturn(Optional.empty());

        assertThat(service.unlikeComment(commentId)).isEqualTo(2);
        verify(commentRepository, never()).adjustLikeCount(any(), anyInt());
    }

    @Test
    void theCommentListResolvesLikedByMeAndTheCount() {
        ForumComment top = ForumComment.builder().discussion(discussion).user(author).content("top").likeCount(5).build();
        UUID cid = UUID.randomUUID();
        ReflectionTestUtils.setField(top, "id", cid);
        when(commentRepository.findTopLevel(eq(DISCUSSION_ID), any())).thenReturn(new PageImpl<>(List.of(top)));
        when(commentReactionRepository.findReactedCommentIds(eq(AUTHOR_ID), anyCollection())).thenReturn(List.of(cid));

        CommentResponse row = service.comments(DISCUSSION_ID, PageRequest.of(0, 20)).content().get(0);

        assertThat(row.likeCount()).isEqualTo(5);
        assertThat(row.likedByMe()).isTrue();
    }

    private ForumComment likableComment(int likeCount) {
        ForumComment comment = ForumComment.builder().discussion(discussion).user(author)
            .content("c").likeCount(likeCount).build();
        ReflectionTestUtils.setField(comment, "id", UUID.randomUUID());
        when(commentRepository.findByIdAndDeletedAtIsNull(comment.getId())).thenReturn(Optional.of(comment));
        return comment;
    }
}
