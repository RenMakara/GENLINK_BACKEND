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
import kh.edu.acleda.campuslink.modules.community.entity.StoryView;
import kh.edu.acleda.campuslink.modules.community.enums.FollowStatus;
import kh.edu.acleda.campuslink.modules.community.enums.StoryMediaType;
import kh.edu.acleda.campuslink.modules.community.enums.StoryReplyPermission;
import kh.edu.acleda.campuslink.modules.community.enums.Visibility;
import kh.edu.acleda.campuslink.modules.community.repository.FollowRepository;
import kh.edu.acleda.campuslink.modules.community.repository.StoryRepository;
import kh.edu.acleda.campuslink.modules.community.repository.StoryViewRepository;
import kh.edu.acleda.campuslink.modules.community.service.impl.StoryServiceImpl;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
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

    @Mock private StoryRepository storyRepository;
    @Mock private StoryViewRepository storyViewRepository;
    @Mock private FollowRepository followRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private StoryServiceImpl service;
    private User me;
    private User other;

    @BeforeEach
    void setUp() {
        service = new StoryServiceImpl(storyRepository, storyViewRepository, followRepository,
            userRepository, currentUserProvider);

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
            "https://cdn/x.jpg",
            StoryMediaType.IMAGE,
            "Friends only story",
            Visibility.FRIENDS,
            StoryReplyPermission.FRIENDS,
            false
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
    void createStoryWithOnlyMeSavesCorrectly() {
        StoryCreateRequest request = new StoryCreateRequest(
            "https://cdn/x.jpg",
            StoryMediaType.VIDEO,
            "Private story",
            Visibility.ONLY_ME,
            StoryReplyPermission.OFF,
            true
        );

        StoryResponse response = service.createStory(request);

        ArgumentCaptor<Story> storyCaptor = ArgumentCaptor.forClass(Story.class);
        verify(storyRepository).save(storyCaptor.capture());
        Story saved = storyCaptor.getValue();

        assertThat(saved.getVisibility()).isEqualTo(Visibility.ONLY_ME);
        assertThat(saved.getReplyPermission()).isEqualTo(StoryReplyPermission.OFF);
        assertThat(saved.isAllowReactions()).isTrue();

        assertThat(response.visibility()).isEqualTo(Visibility.ONLY_ME);
        assertThat(response.replyPermission()).isEqualTo(StoryReplyPermission.OFF);
        assertThat(response.allowReactions()).isTrue();
    }

    @Test
    void createStoryRejectsCustomVisibility() {
        StoryCreateRequest request = new StoryCreateRequest(
            "https://cdn/x.jpg",
            StoryMediaType.IMAGE,
            "Custom audience",
            Visibility.CUSTOM,
            StoryReplyPermission.EVERYONE,
            true
        );

        assertThatThrownBy(() -> service.createStory(request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Custom visibility is not currently supported");
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
    void getStoryByIdAllowsOwnerRegardlessOfVisibility() {
        Story mine = story(STORY_ID, me, Instant.now().plus(Duration.ofHours(1)), Visibility.ONLY_ME);
        when(storyRepository.findByIdAndDeletedAtIsNull(STORY_ID)).thenReturn(Optional.of(mine));

        StoryResponse response = service.getStoryById(STORY_ID);

        assertThat(response).isNotNull();
        assertThat(response.storyId()).isEqualTo(STORY_ID);
        assertThat(response.visibility()).isEqualTo(Visibility.ONLY_ME);
        assertThat(response.seen()).isTrue();
    }

    @Test
    void getStoryByIdAllowsPublicStoryForAnyUser() {
        Story theirs = story(STORY_ID, other, Instant.now().plus(Duration.ofHours(1)), Visibility.PUBLIC);
        when(storyRepository.findByIdAndDeletedAtIsNull(STORY_ID)).thenReturn(Optional.of(theirs));
        when(storyViewRepository.existsByStoryIdAndViewerId(STORY_ID, ME_ID)).thenReturn(false);

        StoryResponse response = service.getStoryById(STORY_ID);

        assertThat(response).isNotNull();
        assertThat(response.storyId()).isEqualTo(STORY_ID);
        assertThat(response.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(response.seen()).isFalse();
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
        Story theirs = story(STORY_ID, other, Instant.now().plus(Duration.ofHours(1)), Visibility.ONLY_ME);
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
        Story theirs = story(STORY_ID, other, Instant.now().plus(Duration.ofHours(1)), Visibility.ONLY_ME);
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
