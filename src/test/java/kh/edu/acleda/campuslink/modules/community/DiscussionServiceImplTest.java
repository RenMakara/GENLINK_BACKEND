package kh.edu.acleda.campuslink.modules.community;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.DiscussionCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.DiscussionReportRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.DiscussionResponse;
import kh.edu.acleda.campuslink.modules.community.entity.DiscussionReport;
import kh.edu.acleda.campuslink.modules.community.entity.ForumDiscussion;
import kh.edu.acleda.campuslink.modules.community.entity.ForumReaction;
import kh.edu.acleda.campuslink.modules.community.repository.DiscussionReportRepository;
import kh.edu.acleda.campuslink.modules.community.repository.ForumCommentRepository;
import kh.edu.acleda.campuslink.modules.community.repository.ForumDiscussionRepository;
import kh.edu.acleda.campuslink.modules.community.repository.ForumReactionRepository;
import kh.edu.acleda.campuslink.modules.community.service.impl.DiscussionServiceImpl;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    @Mock private DiscussionReportRepository reportRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private DiscussionServiceImpl service;
    private User author;
    private ForumDiscussion discussion;

    @BeforeEach
    void setUp() {
        service = new DiscussionServiceImpl(discussionRepository, commentRepository, reactionRepository,
            reportRepository, userRepository, currentUserProvider);

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
    }

    @Test
    void deletingSomeoneElsesDiscussionReadsAsNotFound() {
        when(discussionRepository.findByIdAndUserIdAndDeletedAtIsNull(DISCUSSION_ID, AUTHOR_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(DISCUSSION_ID)).isInstanceOf(NotFoundException.class);
    }
}
