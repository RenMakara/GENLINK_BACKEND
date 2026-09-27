package kh.edu.acleda.campuslink.modules.profile;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.entity.Event;
import kh.edu.acleda.campuslink.modules.community.enums.EventStatus;
import kh.edu.acleda.campuslink.modules.community.repository.EventRepository;
import kh.edu.acleda.campuslink.modules.community.repository.ForumDiscussionRepository;
import kh.edu.acleda.campuslink.modules.community.repository.OpportunityRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PostRepository;
import kh.edu.acleda.campuslink.modules.community.entity.Post;
import kh.edu.acleda.campuslink.modules.community.enums.Visibility;
import kh.edu.acleda.campuslink.modules.community.service.PostVisibilityService;
import kh.edu.acleda.campuslink.modules.entertainment.repository.ShortContentRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.FinancialLessonRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.MotivationContentRepository;
import kh.edu.acleda.campuslink.modules.profile.enums.SavedItemType;
import kh.edu.acleda.campuslink.modules.profile.service.SavedItemPreview;
import kh.edu.acleda.campuslink.modules.profile.service.SavedItemPreviewResolver;
import kh.edu.acleda.campuslink.modules.media.MediaService;
import kh.edu.acleda.campuslink.modules.media.entity.MediaAttachmentRole;
import kh.edu.acleda.campuslink.modules.media.entity.MediaResourceType;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SavedItemPreviewResolverTest {

    private static final UUID VIEWER_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID DRAFT_ID = UUID.randomUUID();
    private static final UUID CANCELLED_ID = UUID.randomUUID();
    private static final UUID PUBLISHED_ID = UUID.randomUUID();

    @Mock private PostRepository postRepository;
    @Mock private ForumDiscussionRepository forumDiscussionRepository;
    @Mock private EventRepository eventRepository;
    @Mock private OpportunityRepository opportunityRepository;
    @Mock private MotivationContentRepository motivationContentRepository;
    @Mock private FinancialLessonRepository financialLessonRepository;
    @Mock private ShortContentRepository shortContentRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private PostVisibilityService postVisibilityService;
    @Mock private MediaService mediaService;

    private SavedItemPreviewResolver resolver;
    private User owner;

    @BeforeEach
    void setUp() {
        resolver = new SavedItemPreviewResolver(postRepository, forumDiscussionRepository,
            eventRepository, opportunityRepository, motivationContentRepository,
            financialLessonRepository, shortContentRepository, currentUserProvider,
            postVisibilityService, mediaService);
        owner = new User();
        ReflectionTestUtils.setField(owner, "id", OWNER_ID);
    }

    @Test
    void savedEventsHideNonPublishedRowsFromNonOwner() {
        Event draft = event(DRAFT_ID, EventStatus.DRAFT);
        Event cancelled = event(CANCELLED_ID, EventStatus.CANCELLED);
        Event published = event(PUBLISHED_ID, EventStatus.PUBLISHED);
        List<UUID> ids = List.of(DRAFT_ID, CANCELLED_ID, PUBLISHED_ID);
        when(currentUserProvider.getCurrentUserId()).thenReturn(VIEWER_ID);
        when(eventRepository.findAllById(ids)).thenReturn(List.of(draft, cancelled, published));

        Map<UUID, SavedItemPreview> previews = resolver.resolve(SavedItemType.EVENT, ids, Language.ENGLISH);

        assertThat(previews).containsOnlyKeys(PUBLISHED_ID);
    }

    @Test
    void eventOwnerCanSeeOwnDraftInSavedEvents() {
        Event draft = event(DRAFT_ID, EventStatus.DRAFT);
        when(currentUserProvider.getCurrentUserId()).thenReturn(OWNER_ID);
        when(eventRepository.findAllById(any())).thenReturn(List.of(draft));

        Map<UUID, SavedItemPreview> previews = resolver.resolve(
            SavedItemType.EVENT, List.of(DRAFT_ID), Language.ENGLISH);

        assertThat(previews).containsKey(DRAFT_ID);
    }

    @Test
    void savedRestrictedPostPreviewChecksAudienceAndResolvesMediaAfterAuthorization() {
        Post post = Post.builder().user(owner).visibility(Visibility.FRIENDS)
            .contentText("A private post").build();
        ReflectionTestUtils.setField(post, "id", PUBLISHED_ID);
        when(currentUserProvider.getCurrentUserId()).thenReturn(VIEWER_ID);
        when(postRepository.findAllById(List.of(PUBLISHED_ID))).thenReturn(List.of(post));
        when(postVisibilityService.canView(post, VIEWER_ID)).thenReturn(true);
        when(mediaService.resolveAttachedMedia(MediaResourceType.POST, PUBLISHED_ID)).thenReturn(List.of(
            new MediaService.AttachedMedia(MediaAttachmentRole.IMAGE, 0, "https://signed/private.jpg")));

        Map<UUID, SavedItemPreview> previews = resolver.resolve(
            SavedItemType.POST, List.of(PUBLISHED_ID), Language.ENGLISH);

        assertThat(previews).containsKey(PUBLISHED_ID);
        assertThat(previews.get(PUBLISHED_ID).imageUrl()).isEqualTo("https://signed/private.jpg");
        verify(postVisibilityService).canView(post, VIEWER_ID);
        verify(mediaService).resolveAttachedMedia(MediaResourceType.POST, PUBLISHED_ID);
    }

    @Test
    void unauthorizedSavedPostPreviewDoesNotResolveItsMedia() {
        Post post = Post.builder().user(owner).visibility(Visibility.ONLY_ME)
            .contentText("Secret").build();
        ReflectionTestUtils.setField(post, "id", PUBLISHED_ID);
        when(currentUserProvider.getCurrentUserId()).thenReturn(VIEWER_ID);
        when(postRepository.findAllById(List.of(PUBLISHED_ID))).thenReturn(List.of(post));

        Map<UUID, SavedItemPreview> previews = resolver.resolve(
            SavedItemType.POST, List.of(PUBLISHED_ID), Language.ENGLISH);

        assertThat(previews).doesNotContainKey(PUBLISHED_ID);
        verify(mediaService, never()).resolveAttachedMedia(MediaResourceType.POST, PUBLISHED_ID);
    }

    private Event event(UUID id, EventStatus status) {
        Event event = Event.builder().organizer(owner).title(status.name() + " Event")
            .status(status).build();
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }
}
