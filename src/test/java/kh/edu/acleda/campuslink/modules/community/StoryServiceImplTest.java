package kh.edu.acleda.campuslink.modules.community;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.StoryCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.StoryFeedItem;
import kh.edu.acleda.campuslink.modules.community.dto.response.StoryResponse;
import kh.edu.acleda.campuslink.modules.community.entity.Story;
import kh.edu.acleda.campuslink.modules.community.entity.StoryReaction;
import kh.edu.acleda.campuslink.modules.community.entity.StoryView;
import kh.edu.acleda.campuslink.modules.community.enums.FollowStatus;
import kh.edu.acleda.campuslink.modules.community.enums.StoryMediaType;
import kh.edu.acleda.campuslink.modules.community.enums.StoryReplyPermission;
import kh.edu.acleda.campuslink.modules.community.enums.Visibility;
import kh.edu.acleda.campuslink.modules.community.repository.FollowRepository;
import kh.edu.acleda.campuslink.modules.community.repository.StoryRepository;
import kh.edu.acleda.campuslink.modules.community.repository.StoryReactionRepository;
import kh.edu.acleda.campuslink.modules.community.repository.StoryViewRepository;
import kh.edu.acleda.campuslink.modules.community.service.impl.StoryServiceImpl;
import kh.edu.acleda.campuslink.modules.media.MediaService;
import kh.edu.acleda.campuslink.modules.media.entity.MediaAttachmentRole;
import kh.edu.acleda.campuslink.modules.media.entity.MediaResourceType;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationTemplateRepository;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoryServiceImplTest {

    private static final UUID ME_ID = UUID.randomUUID();
    private static final UUID OTHER_ID = UUID.randomUUID();
    private static final UUID STORY_ID = UUID.randomUUID();
    private static final UUID MEDIA_ID = UUID.randomUUID();

    @Mock private StoryRepository storyRepository;
    @Mock private StoryViewRepository storyViewRepository;
    @Mock private StoryReactionRepository storyReactionRepository;
    @Mock private FollowRepository followRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private NotificationEmitter notificationEmitter;
    @Mock private NotificationTemplateRepository notificationTemplateRepository;
    @Mock private MediaService mediaService;

    private StoryServiceImpl service;
    private User me;
    private User other;

    @BeforeEach
    void setUp() {
        service = new StoryServiceImpl(storyRepository, storyViewRepository, storyReactionRepository,
            followRepository, userRepository, currentUserProvider, notificationEmitter,
            notificationTemplateRepository, mediaService);

        me = user(ME_ID, "me");
        other = user(OTHER_ID, "other");

        when(currentUserProvider.getCurrentUserId()).thenReturn(ME_ID);
        when(userRepository.findById(ME_ID)).thenReturn(Optional.of(me));
        when(storyRepository.save(any(Story.class))).thenAnswer(invocation -> {
            Story saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            }
            return saved;
        });
        when(mediaService.resolveAttachedMedia(any(), any())).thenReturn(List.of());
    }

    @Test
    void createStorySetsA24hExpiryAndReadsAsSeenForTheAuthor() {
        Instant before = Instant.now();

        StoryResponse response = service.createStory(
            new StoryCreateRequest("https://cdn/x.jpg", StoryMediaType.IMAGE, "hi"));

        verify(storyRepository).save(any(Story.class));
        assertThat(response.seen()).isTrue();
        assertThat(response.mediaType()).isEqualTo(StoryMediaType.IMAGE);
        assertThat(response.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(response.replyPermission()).isEqualTo(StoryReplyPermission.EVERYONE);
        assertThat(response.allowReactions()).isTrue();
        assertThat(response.expiresAt()).isBetween(before.plus(Duration.ofHours(24)).minusSeconds(5),
            Instant.now().plus(Duration.ofHours(24)).plusSeconds(5));
    }

    @Test
    void createStoryWithCustomPrivacySettingsSavesAndReturnsActualValues() {
        StoryCreateRequest request = new StoryCreateRequest(
            null,
            StoryMediaType.IMAGE,
            "Friends only story",
            Visibility.FRIENDS,
            StoryReplyPermission.FRIENDS,
            false,
            MEDIA_ID
        );

        StoryResponse response = service.createStory(request);

        ArgumentCaptor<Story> storyCaptor = ArgumentCaptor.forClass(Story.class);
        verify(storyRepository).save(storyCaptor.capture());
        Story saved = storyCaptor.getValue();

        assertThat(saved.getVisibility()).isEqualTo(Visibility.FRIENDS);
        assertThat(saved.getReplyPermission()).isEqualTo(StoryReplyPermission.FRIENDS);
        assertThat(saved.isAllowReactions()).isFalse();

        assertThat(response.visibility()).isEqualTo(Visibility.FRIENDS);
        assertThat(response.replyPermission()).isEqualTo(StoryReplyPermission.FRIENDS);
        assertThat(response.allowReactions()).isFalse();
    }

    @Test
    void createStoryWithPublicVisibilitySavesCorrectly() {
        StoryCreateRequest request = new StoryCreateRequest(
            "https://cdn/x.jpg",
            StoryMediaType.VIDEO,
            "Private story",
            Visibility.PUBLIC,
            StoryReplyPermission.OFF,
            true
        );

        StoryResponse response = service.createStory(request);

        ArgumentCaptor<Story> storyCaptor = ArgumentCaptor.forClass(Story.class);
        verify(storyRepository).save(storyCaptor.capture());
        Story saved = storyCaptor.getValue();

        assertThat(saved.getVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(saved.getReplyPermission()).isEqualTo(StoryReplyPermission.OFF);
        assertThat(saved.isAllowReactions()).isTrue();

        assertThat(response.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(response.replyPermission()).isEqualTo(StoryReplyPermission.OFF);
        assertThat(response.allowReactions()).isTrue();
    }

    @Test
    void createStoryWithOnlyMeVisibilitySavesCorrectly() {
        StoryCreateRequest request = new StoryCreateRequest(
            null,
            StoryMediaType.IMAGE,
            "Only me story",
            Visibility.ONLY_ME,
            StoryReplyPermission.OFF,
            false,
            MEDIA_ID
        );

        StoryResponse response = service.createStory(request);

        ArgumentCaptor<Story> storyCaptor = ArgumentCaptor.forClass(Story.class);
        verify(storyRepository).save(storyCaptor.capture());
        Story saved = storyCaptor.getValue();

        assertThat(saved.getVisibility()).isEqualTo(Visibility.ONLY_ME);
        assertThat(saved.getReplyPermission()).isEqualTo(StoryReplyPermission.OFF);
        assertThat(saved.isAllowReactions()).isFalse();

        assertThat(response.visibility()).isEqualTo(Visibility.ONLY_ME);
        assertThat(response.replyPermission()).isEqualTo(StoryReplyPermission.OFF);
        assertThat(response.allowReactions()).isFalse();
    }

    @Test
    void restrictedStoryRejectsAnUnownedRawMediaUrl() {
        StoryCreateRequest request = new StoryCreateRequest("https://cdn/private.jpg",
            StoryMediaType.IMAGE, "Friends only", Visibility.FRIENDS,
            StoryReplyPermission.FRIENDS, true);

        assertThatThrownBy(() -> service.createStory(request))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", "PRIVATE_MEDIA_REQUIRED");
    }

    @Test
    void friendsStoryResolvesPrivateMediaOnlyAfterAudienceAuthorization() {
        Story theirs = story(STORY_ID, other, Instant.now().plus(Duration.ofHours(1)), Visibility.FRIENDS);
        when(storyRepository.findByIdAndDeletedAtIsNull(STORY_ID)).thenReturn(Optional.of(theirs));
        when(followRepository.existsByFollowerIdAndFollowingIdAndStatus(
            OTHER_ID, ME_ID, FollowStatus.ACCEPTED)).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFollowingIdAndStatus(
            ME_ID, OTHER_ID, FollowStatus.ACCEPTED)).thenReturn(true);
        when(mediaService.resolveAttachedMedia(MediaResourceType.STORY, STORY_ID))
            .thenReturn(List.of(new MediaService.AttachedMedia(
                MediaAttachmentRole.STORY, 0, "https://signed.example/private")));

        StoryResponse response = service.getStoryById(STORY_ID);

        assertThat(response.mediaUrl()).isEqualTo("https://signed.example/private");
        verify(mediaService).resolveAttachedMedia(MediaResourceType.STORY, STORY_ID);
    }

    @Test
    void unauthorizedViewerCannotResolveFriendsStoryMedia() {
        Story theirs = story(STORY_ID, other, Instant.now().plus(Duration.ofHours(1)), Visibility.FRIENDS);
        when(storyRepository.findByIdAndDeletedAtIsNull(STORY_ID)).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> service.getStoryById(STORY_ID)).isInstanceOf(NotFoundException.class);

        verify(mediaService, never()).resolveAttachedMedia(MediaResourceType.STORY, STORY_ID);
    }

    @Test
    void createStoryRejectsCommunityVisibilityWhenExplicitlyRequested() {
        StoryCreateRequest request = new StoryCreateRequest(
            "https://cdn/x.jpg",
            StoryMediaType.IMAGE,
            "Community audience",
            Visibility.COMMUNITY,
            StoryReplyPermission.EVERYONE,
            true
        );

        assertThatThrownBy(() -> service.createStory(request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Community visibility is not supported");
        verify(storyRepository, never()).save(any(Story.class));
    }

    @Test
    void createStoryRejectsCommunityVisibility() {
        StoryCreateRequest request = new StoryCreateRequest(
            "https://cdn/x.jpg",
            StoryMediaType.IMAGE,
            "Community story",
            Visibility.COMMUNITY,
            StoryReplyPermission.EVERYONE,
            true
        );

        assertThatThrownBy(() -> service.createStory(request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Community visibility is not supported");
        verify(storyRepository, never()).save(any(Story.class));
    }

    @Test
    void feedPutsOwnGroupFirstAndFlagsUnseenOnOthers() {
        Story mine = story(UUID.randomUUID(), me, Instant.now().plus(Duration.ofHours(1)), Visibility.PUBLIC);
        Story theirs = story(UUID.randomUUID(), other, Instant.now().plus(Duration.ofHours(1)), Visibility.PUBLIC);
        when(followRepository.findFollowingIds(ME_ID)).thenReturn(List.of(OTHER_ID));
        when(followRepository.findFriendIds(ME_ID)).thenReturn(List.of());
        when(storyRepository.findVisibleActiveStories(anyCollection(), eq(ME_ID), anyCollection(), any(Instant.class)))
            .thenReturn(List.of(theirs, mine));
        // The viewer has seen none of them.
        when(storyViewRepository.findViewedStoryIds(any(), anyCollection())).thenReturn(List.of());

        List<StoryFeedItem> feed = service.getStoriesFeed();

        assertThat(feed).hasSize(2);
        assertThat(feed.get(0).author().userId()).isEqualTo(ME_ID);
        assertThat(feed.get(0).hasUnseen()).isFalse();
        StoryFeedItem othersItem = feed.get(1);
        assertThat(othersItem.author().userId()).isEqualTo(OTHER_ID);
        assertThat(othersItem.hasUnseen()).isTrue();
        assertThat(othersItem.stories().get(0).seen()).isFalse();
    }

    @Test
    void feedIncludesPersistedReactionCountAndViewerState() {
        Story theirs = story(STORY_ID, other, Instant.now().plus(Duration.ofHours(1)), Visibility.PUBLIC);
        when(followRepository.findFollowingIds(ME_ID)).thenReturn(List.of(OTHER_ID));
        when(followRepository.findFriendIds(ME_ID)).thenReturn(List.of());
        when(storyRepository.findVisibleActiveStories(anyCollection(), eq(ME_ID), anyCollection(), any(Instant.class)))
            .thenReturn(List.of(theirs));
        when(storyViewRepository.findViewedStoryIds(any(), anyCollection())).thenReturn(List.of());
        when(storyReactionRepository.countByStoryIds(anyCollection()))
            .thenReturn(List.<Object[]>of(new Object[] {STORY_ID, 2L}));
        when(storyReactionRepository.findReactedStoryIds(eq(ME_ID), anyCollection()))
            .thenReturn(List.of(STORY_ID));

        List<StoryFeedItem> feed = service.getStoriesFeed();

        StoryResponse response = feed.get(0).stories().get(0);
        assertThat(response.reactionCount()).isEqualTo(2);
        assertThat(response.reactedByMe()).isTrue();
    }

    @Test
    void getStoryByIdAllowsOwnerRegardlessOfVisibility() {
        Story mine = story(STORY_ID, me, Instant.now().plus(Duration.ofHours(1)), Visibility.COMMUNITY);
        when(storyRepository.findByIdAndDeletedAtIsNull(STORY_ID)).thenReturn(Optional.of(mine));

        StoryResponse response = service.getStoryById(STORY_ID);

        assertThat(response).isNotNull();
        assertThat(response.storyId()).isEqualTo(STORY_ID);
        assertThat(response.visibility()).isEqualTo(Visibility.COMMUNITY);
        assertThat(response.seen()).isTrue();
    }

    @Test
    void getStoryByIdAllowsPublicStoryForAnyUser() {
        Story theirs = story(STORY_ID, other, Instant.now().plus(Duration.ofHours(1)), Visibility.PUBLIC);
        when(storyRepository.findByIdAndDeletedAtIsNull(STORY_ID)).thenReturn(Optional.of(theirs));
        when(storyViewRepository.existsByStoryIdAndViewerId(STORY_ID, ME_ID)).thenReturn(false);
        when(storyReactionRepository.countByStoryId(STORY_ID)).thenReturn(3L);
        when(storyReactionRepository.findByStoryIdAndUserId(STORY_ID, ME_ID))
            .thenReturn(Optional.of(StoryReaction.builder().story(theirs).user(me).reactionType("LIKE").build()));

        StoryResponse response = service.getStoryById(STORY_ID);

        assertThat(response).isNotNull();
        assertThat(response.storyId()).isEqualTo(STORY_ID);
        assertThat(response.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(response.seen()).isFalse();
        assertThat(response.reactionCount()).isEqualTo(3);
        assertThat(response.reactedByMe()).isTrue();
    }

    @Test
    void getStoryByIdAllowsFriendsStoryWhenMutualFriends() {
        Story theirs = story(STORY_ID, other, Instant.now().plus(Duration.ofHours(1)), Visibility.FRIENDS);
        when(storyRepository.findByIdAndDeletedAtIsNull(STORY_ID)).thenReturn(Optional.of(theirs));
        when(followRepository.existsByFollowerIdAndFollowingIdAndStatus(OTHER_ID, ME_ID, FollowStatus.ACCEPTED)).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFollowingIdAndStatus(ME_ID, OTHER_ID, FollowStatus.ACCEPTED)).thenReturn(true);

        StoryResponse response = service.getStoryById(STORY_ID);

        assertThat(response).isNotNull();
        assertThat(response.storyId()).isEqualTo(STORY_ID);
        assertThat(response.visibility()).isEqualTo(Visibility.FRIENDS);
    }

    @Test
    void getStoryByIdRejectsFriendsStoryWhenNotMutualFriends() {
        Story theirs = story(STORY_ID, other, Instant.now().plus(Duration.ofHours(1)), Visibility.FRIENDS);
        when(storyRepository.findByIdAndDeletedAtIsNull(STORY_ID)).thenReturn(Optional.of(theirs));
        // ME_ID follows OTHER_ID, but OTHER_ID does NOT follow ME_ID
        when(followRepository.existsByFollowerIdAndFollowingIdAndStatus(ME_ID, OTHER_ID, FollowStatus.ACCEPTED)).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFollowingIdAndStatus(OTHER_ID, ME_ID, FollowStatus.ACCEPTED)).thenReturn(false);

        assertThatThrownBy(() -> service.getStoryById(STORY_ID))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Story not found");
    }

    @Test
    void getStoryByIdRejectsOnlyMeStoryForNonOwner() {
        Story theirs = story(STORY_ID, other, Instant.now().plus(Duration.ofHours(1)), Visibility.COMMUNITY);
        when(storyRepository.findByIdAndDeletedAtIsNull(STORY_ID)).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> service.getStoryById(STORY_ID))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Story not found");
    }

    @Test
    void getStoryByIdRejectsExpiredStory() {
        Story expired = story(STORY_ID, other, Instant.now().minus(Duration.ofHours(1)), Visibility.PUBLIC);
        when(storyRepository.findByIdAndDeletedAtIsNull(STORY_ID)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.getStoryById(STORY_ID))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Story not found");
    }

    @Test
    void viewingYourOwnStoryRecordsNoView() {
        Story mine = story(STORY_ID, me, Instant.now().plus(Duration.ofHours(1)), Visibility.PUBLIC);
        when(storyRepository.findByIdAndDeletedAtIsNull(STORY_ID)).thenReturn(Optional.of(mine));

        service.viewStory(STORY_ID);

        verify(storyViewRepository, never()).save(any(StoryView.class));
    }

    @Test
    void firstViewOfAnothersStoryIsRecordedAndRepeatIsANoOp() {
        Story theirs = story(STORY_ID, other, Instant.now().plus(Duration.ofHours(1)), Visibility.PUBLIC);
        when(storyRepository.findByIdAndDeletedAtIsNull(STORY_ID)).thenReturn(Optional.of(theirs));
        when(storyViewRepository.existsByStoryIdAndViewerId(STORY_ID, ME_ID)).thenReturn(false);

        service.viewStory(STORY_ID);
        verify(storyViewRepository).save(any(StoryView.class));

        when(storyViewRepository.existsByStoryIdAndViewerId(STORY_ID, ME_ID)).thenReturn(true);
        service.viewStory(STORY_ID);
        // Still just the one save from the first view.
        verify(storyViewRepository).save(any(StoryView.class));
    }

    @Test
    void viewStoryRejectsUnauthorizedViewerWithoutRecordingView() {
        Story theirs = story(STORY_ID, other, Instant.now().plus(Duration.ofHours(1)), Visibility.FRIENDS);
        when(storyRepository.findByIdAndDeletedAtIsNull(STORY_ID)).thenReturn(Optional.of(theirs));
        when(followRepository.existsByFollowerIdAndFollowingIdAndStatus(OTHER_ID, ME_ID, FollowStatus.ACCEPTED)).thenReturn(false);
        when(followRepository.existsByFollowerIdAndFollowingIdAndStatus(ME_ID, OTHER_ID, FollowStatus.ACCEPTED)).thenReturn(false);

        assertThatThrownBy(() -> service.viewStory(STORY_ID))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Story not found");
        verify(storyViewRepository, never()).save(any(StoryView.class));
    }

    @Test
    void viewStoryRejectsOnlyMeForNonOwnerWithoutRecordingView() {
        Story theirs = story(STORY_ID, other, Instant.now().plus(Duration.ofHours(1)), Visibility.COMMUNITY);
        when(storyRepository.findByIdAndDeletedAtIsNull(STORY_ID)).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> service.viewStory(STORY_ID))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Story not found");
        verify(storyViewRepository, never()).save(any(StoryView.class));
    }

    @Test
    void viewingAnExpiredStoryIsNotFound() {
        Story expired = story(STORY_ID, other, Instant.now().minus(Duration.ofHours(1)), Visibility.PUBLIC);
        when(storyRepository.findByIdAndDeletedAtIsNull(STORY_ID)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.viewStory(STORY_ID)).isInstanceOf(NotFoundException.class);
        verify(storyViewRepository, never()).save(any(StoryView.class));
    }

    @Test
    void onlyTheOwnerCanSeeTheViewerList() {
        Story theirs = story(STORY_ID, other, Instant.now().plus(Duration.ofHours(1)), Visibility.PUBLIC);
        when(storyRepository.findByIdAndDeletedAtIsNull(STORY_ID)).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> service.storyViewers(STORY_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deletingSomeoneElsesStoryReadsAsNotFound() {
        Story theirs = story(STORY_ID, other, Instant.now().plus(Duration.ofHours(1)), Visibility.PUBLIC);
        when(storyRepository.findByIdAndDeletedAtIsNull(STORY_ID)).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> service.deleteStory(STORY_ID)).isInstanceOf(NotFoundException.class);
        assertThat(theirs.getDeletedAt()).isNull();
    }

    @Test
    void deletingYourOwnStoryIsASoftDelete() {
        Story mine = story(STORY_ID, me, Instant.now().plus(Duration.ofHours(1)), Visibility.PUBLIC);
        when(storyRepository.findByIdAndDeletedAtIsNull(STORY_ID)).thenReturn(Optional.of(mine));

        service.deleteStory(STORY_ID);

        assertThat(mine.getDeletedAt()).isNotNull();
        verify(storyRepository).save(mine);
    }

    @Test
    void likeStoryEmitsNotificationToStoryAuthor() {
        Story theirs = story(STORY_ID, other, Instant.now().plus(Duration.ofHours(1)), Visibility.PUBLIC);
        when(storyRepository.findByIdAndDeletedAtIsNullForUpdate(STORY_ID)).thenReturn(Optional.of(theirs));
        when(storyReactionRepository.findByStoryIdAndUserId(STORY_ID, ME_ID)).thenReturn(Optional.empty());
        when(notificationTemplateRepository.existsByTemplateKey("COMMUNITY_STORY_LIKE")).thenReturn(true);

        service.likeStory(STORY_ID);

        verify(storyRepository).findByIdAndDeletedAtIsNullForUpdate(STORY_ID);
        ArgumentCaptor<StoryReaction> reactionCaptor = ArgumentCaptor.forClass(StoryReaction.class);
        verify(storyReactionRepository).save(reactionCaptor.capture());
        assertThat(reactionCaptor.getValue().getStory()).isSameAs(theirs);
        assertThat(reactionCaptor.getValue().getUser()).isSameAs(me);
        assertThat(reactionCaptor.getValue().getReactionType()).isEqualTo("LIKE");

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationEmitter).emit(captor.capture());

        NotificationRequest req = captor.getValue();
        assertThat(req.recipientUserId()).isEqualTo(OTHER_ID);
        assertThat(req.templateKey()).isEqualTo("COMMUNITY_STORY_LIKE");
        assertThat(req.referenceType()).isEqualTo("STORY");
        assertThat(req.referenceId()).isEqualTo(STORY_ID);
        assertThat(req.parameters()).containsEntry("likerName", "me");
    }

    @Test
    void repeatedLikeStoryKeepsTheExistingReactionAndDoesNotSendAnotherNotification() {
        Story theirs = story(STORY_ID, other, Instant.now().plus(Duration.ofHours(1)), Visibility.PUBLIC);
        StoryReaction existing = StoryReaction.builder()
            .story(theirs)
            .user(me)
            .reactionType("LIKE")
            .build();
        when(storyRepository.findByIdAndDeletedAtIsNullForUpdate(STORY_ID)).thenReturn(Optional.of(theirs));
        when(storyReactionRepository.findByStoryIdAndUserId(STORY_ID, ME_ID))
            .thenReturn(Optional.of(existing));

        service.likeStory(STORY_ID);

        verify(storyRepository).findByIdAndDeletedAtIsNullForUpdate(STORY_ID);
        verify(storyReactionRepository, never()).delete(existing);
        verify(storyReactionRepository, never()).save(any(StoryReaction.class));
        verify(notificationEmitter, never()).emit(any());
    }

    @Test
    void unlikeStoryRemovesAnExistingReactionOnce() {
        Story theirs = story(STORY_ID, other, Instant.now().plus(Duration.ofHours(1)), Visibility.PUBLIC);
        StoryReaction existing = StoryReaction.builder()
            .story(theirs)
            .user(me)
            .reactionType("LIKE")
            .build();
        when(storyRepository.findByIdAndDeletedAtIsNullForUpdate(STORY_ID)).thenReturn(Optional.of(theirs));
        when(storyReactionRepository.findByStoryIdAndUserId(STORY_ID, ME_ID))
            .thenReturn(Optional.of(existing));

        service.unlikeStory(STORY_ID);

        verify(storyReactionRepository).delete(existing);
        verify(notificationEmitter, never()).emit(any());
    }

    @Test
    void likeStoryDoesNotEmitNotificationWhenAuthorIsViewer() {
        Story mine = story(STORY_ID, me, Instant.now().plus(Duration.ofHours(1)), Visibility.PUBLIC);
        when(storyRepository.findByIdAndDeletedAtIsNullForUpdate(STORY_ID)).thenReturn(Optional.of(mine));

        service.likeStory(STORY_ID);

        verify(notificationEmitter, never()).emit(any());
    }

    @Test
    void likeStoryDoesNotEmitNotificationWhenReactionsDisabled() {
        Story theirs = story(STORY_ID, other, Instant.now().plus(Duration.ofHours(1)), Visibility.PUBLIC);
        theirs.setAllowReactions(false);
        when(storyRepository.findByIdAndDeletedAtIsNullForUpdate(STORY_ID)).thenReturn(Optional.of(theirs));

        service.likeStory(STORY_ID);

        verify(notificationEmitter, never()).emit(any());
    }

    private User user(UUID id, String username) {
        User user = new User();
        user.setUsername(username);
        user.setFullName(username);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Story story(UUID id, User author, Instant expiresAt, Visibility visibility) {
        Story story = Story.builder()
            .user(author)
            .mediaUrl("https://cdn/x.jpg")
            .mediaType(StoryMediaType.IMAGE)
            .visibility(visibility)
            .expiresAt(expiresAt)
            .build();
        ReflectionTestUtils.setField(story, "id", id);
        return story;
    }
}
