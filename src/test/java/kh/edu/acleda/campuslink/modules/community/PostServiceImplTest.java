package kh.edu.acleda.campuslink.modules.community;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.PostCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.PostReportRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.RepostRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.ReportedPostResponse;
import kh.edu.acleda.campuslink.modules.community.entity.Community;
import kh.edu.acleda.campuslink.modules.community.entity.CommunityMember;
import kh.edu.acleda.campuslink.modules.community.entity.Post;
import kh.edu.acleda.campuslink.modules.community.entity.PostReaction;
import kh.edu.acleda.campuslink.modules.community.entity.PostReport;
import kh.edu.acleda.campuslink.modules.community.entity.PostRepost;
import kh.edu.acleda.campuslink.modules.community.repository.PostRepostRepository;
import kh.edu.acleda.campuslink.modules.community.enums.CommunityRole;
import kh.edu.acleda.campuslink.modules.community.enums.MemberStatus;
import kh.edu.acleda.campuslink.modules.community.enums.PostType;
import kh.edu.acleda.campuslink.modules.community.enums.Visibility;
import kh.edu.acleda.campuslink.modules.community.repository.CommunityMemberRepository;
import kh.edu.acleda.campuslink.modules.community.repository.CommunityRepository;
import kh.edu.acleda.campuslink.modules.community.repository.FollowRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PostReactionRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PostReportRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PostRepository;
import kh.edu.acleda.campuslink.modules.community.service.HashtagService;
import kh.edu.acleda.campuslink.modules.community.service.PostVisibilityService;
import kh.edu.acleda.campuslink.modules.community.service.impl.PostServiceImpl;
import kh.edu.acleda.campuslink.modules.community.dto.request.PollCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.PollVoteRequest;
import kh.edu.acleda.campuslink.modules.community.entity.Poll;
import kh.edu.acleda.campuslink.modules.community.entity.PollOption;
import kh.edu.acleda.campuslink.modules.community.entity.PollVote;
import kh.edu.acleda.campuslink.modules.community.repository.PollRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PollOptionRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PollVoteRepository;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.notification.event.NotificationEventListener;
import kh.edu.acleda.campuslink.modules.notification.event.NotificationRequestedEvent;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationWriter;
import kh.edu.acleda.campuslink.modules.media.MediaService;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.modules.profile.enums.SavedItemType;
import kh.edu.acleda.campuslink.modules.profile.repository.SavedItemRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID POST_ID = UUID.randomUUID();

    @Mock private PostRepository postRepository;
    @Mock private PostReactionRepository postReactionRepository;
    @Mock private PostReportRepository postReportRepository;
    @Mock private PostRepostRepository postRepostRepository;
    @Mock private SavedItemRepository savedItemRepository;
    @Mock private CommunityRepository communityRepository;
    @Mock private CommunityMemberRepository communityMemberRepository;
    @Mock private FollowRepository followRepository;
    @Mock private HashtagService hashtagService;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private PollRepository pollRepository;
    @Mock private PollOptionRepository pollOptionRepository;
    @Mock private PollVoteRepository pollVoteRepository;
    @Mock private NotificationEmitter notificationEmitter;
    @Mock private NotificationWriter notificationWriter;
    @Mock private PostVisibilityService postVisibilityService;
    @Mock private MediaService mediaService;

    private PostServiceImpl service;
    private User author;
    private Post post;

    @BeforeEach
    void setUp() {
        service = new PostServiceImpl(postRepository, postReactionRepository, postReportRepository,
            postRepostRepository, savedItemRepository, communityRepository, communityMemberRepository, followRepository,
            hashtagService, userRepository, currentUserProvider,
            pollRepository, pollOptionRepository, pollVoteRepository, notificationEmitter,
            postVisibilityService, mediaService);

        author = new User();
        author.setEmail("author@example.com");
        author.setFullName("Author");
        author.setUsername("author");
        ReflectionTestUtils.setField(author, "id", USER_ID);

        post = Post.builder().user(author).contentText("Hello #genlink")
            .postType(PostType.TEXT).visibility(Visibility.PUBLIC)
            .likeCount(4).commentCount(2).build();
        ReflectionTestUtils.setField(post, "id", POST_ID);
        when(postRepository.findByIdAndDeletedAtIsNullForUpdate(POST_ID)).thenReturn(Optional.of(post));
        when(postRepository.findByIdAndUserIdAndDeletedAtIsNullForUpdate(POST_ID, USER_ID))
            .thenReturn(Optional.of(post));
        when(mediaService.resolveAttachedMedia(any(), any())).thenReturn(List.of());

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(author));
        // Stand in for JPA assigning the generated id on save; without it the service
        // reads a null id back, which is not how it behaves against a real database.
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            }
            return saved;
        });
        when(hashtagService.tagsForPosts(any())).thenReturn(Map.of());
        when(postReactionRepository.findLikedPostIds(any(), anyCollection())).thenReturn(List.of());
        when(savedItemRepository.findSavedItemIds(any(), any(), anyCollection())).thenReturn(List.of());
        when(postRepostRepository.findByPostIdInAndUserId(anyCollection(), any())).thenReturn(List.of());
        when(followRepository.findFriendIds(any())).thenReturn(List.of());
        when(communityMemberRepository.findActiveCommunityIds(any())).thenReturn(List.of());
        // Most posts are not polls, so poll enrichment resolves to nothing by default.
        when(pollRepository.findByPostIdIn(any())).thenReturn(List.of());
    }

    /** The stub used to echo the request back with a null id and save nothing. */
    @Test
    void createPersistsThePostAndReturnsARealId() {
        PostResponse response = service.create(new PostCreateRequest(
            "Hello #genlink", null, PostType.TEXT, Visibility.PUBLIC, Language.ENGLISH, null));

        verify(postRepository).save(any(Post.class));
        assertThat(response.author().userId()).isEqualTo(USER_ID);
        assertThat(response.likeCount()).isZero();
    }

    @Test
    void createPersistsAndReturnsRichComposerFields() {
        PostResponse response = service.create(new PostCreateRequest(
            "Post with location and link", null, null, PostType.TEXT, Visibility.PUBLIC,
            Language.ENGLISH, null, null, null, "Library", "https://example.com",
            "Example", "example.com", null, null, null, null, false));

        assertThat(response.location()).isEqualTo("Library");
        assertThat(response.linkUrl()).isEqualTo("https://example.com");
        assertThat(response.linkPreviewTitle()).isEqualTo("Example");
        assertThat(response.linkPreviewDomain()).isEqualTo("example.com");
        var captor = org.mockito.ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getLocation()).isEqualTo("Library");
        assertThat(captor.getValue().getLinkUrl()).isEqualTo("https://example.com");
    }

    @Test
    void updatePersistsRichComposerFieldsForPostsOutsideTheFeed() {
        when(postRepository.findByIdAndUserIdAndDeletedAtIsNull(POST_ID, USER_ID))
            .thenReturn(Optional.of(post));
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));

        PostResponse response = service.update(POST_ID, new PostCreateRequest(
            "Edited post", null, null, null, null, null, null, null, null,
            "Student center", "https://example.org", "Example Org", "example.org",
            null, null, null, null, true));

        assertThat(post.getContentText()).isEqualTo("Edited post");
        assertThat(post.getLocation()).isEqualTo("Student center");
        assertThat(post.getLinkUrl()).isEqualTo("https://example.org");
        assertThat(response.location()).isEqualTo("Student center");
        assertThat(response.linkPreviewTitle()).isEqualTo("Example Org");
        verify(postRepository).save(post);
    }

    @Test
    void creatingACommunityPostIncrementsItsCountOnce() {
        Community community = communityWithId();
        when(communityRepository.findByIdAndDeletedAtIsNull(community.getId())).thenReturn(Optional.of(community));
        when(communityMemberRepository.findActiveCommunityIds(USER_ID)).thenReturn(List.of(community.getId()));

        service.create(new PostCreateRequest("community post", null, null, PostType.TEXT,
            Visibility.COMMUNITY, Language.ENGLISH, community.getId()));

        verify(communityRepository, times(1)).adjustPostCount(community.getId(), 1);
    }

    @Test
    void createDefaultsTypeAndVisibilityWhenOmitted() {
        service.create(new PostCreateRequest("no type given", null, null, null, null, null));

        var captor = org.mockito.ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getPostType()).isEqualTo(PostType.TEXT);
        assertThat(captor.getValue().getVisibility()).isEqualTo(Visibility.PUBLIC);
    }

    @Test
    void directPostReadUsesTheCentralVisibilityPolicy() {
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));
        doThrow(new NotFoundException("Post not found"))
            .when(postVisibilityService).requireCanView(post, USER_ID);

        assertThatThrownBy(() -> service.getById(POST_ID)).isInstanceOf(NotFoundException.class);
        verify(mediaService, never()).resolveAttachedMedia(any(), any());
    }

    @Test
    void authorizedRestrictedPostResolvesItsPrivateMediaAfterVisibilityCheck() {
        post.setVisibility(Visibility.FRIENDS);
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));
        when(mediaService.resolveAttachedMedia(any(), eq(POST_ID))).thenReturn(List.of(
            new MediaService.AttachedMedia(
                kh.edu.acleda.campuslink.modules.media.entity.MediaAttachmentRole.IMAGE,
                0, "https://signed.example/private.jpg")));

        PostResponse response = service.getById(POST_ID);

        assertThat(response.imageUrl()).isEqualTo("https://signed.example/private.jpg");
        verify(postVisibilityService).requireCanView(post, USER_ID);
    }

    @Test
    void onlyMeOwnerCanReadPostAndResolveItsPrivateMedia() {
        post.setVisibility(Visibility.ONLY_ME);
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));
        when(mediaService.resolveAttachedMedia(any(), eq(POST_ID))).thenReturn(List.of(
            new MediaService.AttachedMedia(
                kh.edu.acleda.campuslink.modules.media.entity.MediaAttachmentRole.IMAGE,
                0, "https://signed.example/owner-only.jpg")));

        PostResponse response = service.getById(POST_ID);

        assertThat(response.visibility()).isEqualTo(Visibility.ONLY_ME);
        assertThat(response.imageUrl()).isEqualTo("https://signed.example/owner-only.jpg");
        verify(postVisibilityService).requireCanView(post, USER_ID);
        verify(mediaService).resolveAttachedMedia(any(), eq(POST_ID));
    }

    @Test
    void createSyncsHashtags() {
        service.create(new PostCreateRequest("Hello #genlink", null, null, null, null, null));

        verify(hashtagService).syncForPost(any(Post.class), eq("Hello #genlink"));
    }

    @Test
    void createVideoPostPersistsVideoTypeAndVideoUrl() {
        PostResponse response = service.create(new PostCreateRequest(
            "Check this video #flutter", null, "https://example.com/demo.mp4",
            PostType.VIDEO, Visibility.PUBLIC, Language.ENGLISH, null));

        var captor = org.mockito.ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        Post saved = captor.getValue();
        assertThat(saved.getPostType()).isEqualTo(PostType.VIDEO);
        assertThat(saved.getVideoUrl()).isEqualTo("https://example.com/demo.mp4");
        assertThat(response.postType()).isEqualTo(PostType.VIDEO);
        assertThat(response.videoUrl()).isEqualTo("https://example.com/demo.mp4");
        verify(hashtagService).syncForPost(any(Post.class), eq("Check this video #flutter"));
    }

    @Test
    void createVideoPostInfersVideoTypeWhenPostTypeOmitted() {
        service.create(new PostCreateRequest(
            "Video without explicit type", null, "https://example.com/inferred.mp4",
            null, Visibility.PUBLIC, Language.ENGLISH, null));

        var captor = org.mockito.ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getPostType()).isEqualTo(PostType.VIDEO);
        assertThat(captor.getValue().getVideoUrl()).isEqualTo("https://example.com/inferred.mp4");
    }

    @Test
    void createVideoOnlyPostWithoutCaption() {
        PostResponse response = service.create(new PostCreateRequest(
            null, null, "https://example.com/nocaption.mp4",
            PostType.VIDEO, Visibility.PUBLIC, Language.ENGLISH, null));

        var captor = org.mockito.ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getPostType()).isEqualTo(PostType.VIDEO);
        assertThat(captor.getValue().getVideoUrl()).isEqualTo("https://example.com/nocaption.mp4");
        assertThat(captor.getValue().getContentText()).isNull();
        assertThat(response.videoUrl()).isEqualTo("https://example.com/nocaption.mp4");
    }

    @Test
    void updatePostWithVideoUrlAndType() {
        when(postRepository.findByIdAndUserIdAndDeletedAtIsNull(POST_ID, USER_ID)).thenReturn(Optional.of(post));
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));

        PostResponse response = service.update(POST_ID, new PostCreateRequest(
            "Updated video post", null, "https://example.com/updated.mp4",
            PostType.VIDEO, Visibility.PUBLIC, Language.ENGLISH, null));

        assertThat(post.getVideoUrl()).isEqualTo("https://example.com/updated.mp4");
        assertThat(post.getPostType()).isEqualTo(PostType.VIDEO);
        assertThat(response.videoUrl()).isEqualTo("https://example.com/updated.mp4");
        assertThat(response.postType()).isEqualTo(PostType.VIDEO);
    }

    @Test
    void feedRowsCarryAuthorAndFlags() {
        when(postRepository.findVisibleFeed(eq(USER_ID), anyCollection(), anyCollection(), any()))
            .thenReturn(new PageImpl<>(List.of(post)));
        when(postReactionRepository.findLikedPostIds(eq(USER_ID), anyCollection())).thenReturn(List.of(POST_ID));
        when(savedItemRepository.findSavedItemIds(eq(USER_ID), eq(SavedItemType.POST), anyCollection()))
            .thenReturn(List.of(POST_ID));
        when(hashtagService.tagsForPosts(Set.of(POST_ID))).thenReturn(Map.of(POST_ID, List.of("genlink")));

        PageResponse<PostResponse> feed = service.getFeed(PageRequest.of(0, 20));

        PostResponse row = feed.content().get(0);
        assertThat(row.author().username()).isEqualTo("author");
        assertThat(row.likedByMe()).isTrue();
        assertThat(row.savedByMe()).isTrue();
        assertThat(row.hashtags()).containsExactly("genlink");
    }

    /** The whole point of hydrating server-side: one lookup per page, not per row. */
    @Test
    void feedResolvesLikesAndSavesInOneQueryEach() {
        Post second = Post.builder().user(author).likeCount(0).commentCount(0).build();
        ReflectionTestUtils.setField(second, "id", UUID.randomUUID());
        when(postRepository.findVisibleFeed(eq(USER_ID), anyCollection(), anyCollection(), any()))
            .thenReturn(new PageImpl<>(List.of(post, second)));

        service.getFeed(PageRequest.of(0, 20));

        verify(postReactionRepository, times(1)).findLikedPostIds(eq(USER_ID), anyCollection());
        verify(savedItemRepository, times(1)).findSavedItemIds(eq(USER_ID), eq(SavedItemType.POST), anyCollection());
        verify(hashtagService, times(1)).tagsForPosts(any());
    }

    @Test
    void emptyFeedSkipsHydrationQueries() {
        when(postRepository.findVisibleFeed(eq(USER_ID), anyCollection(), anyCollection(), any())).thenReturn(new PageImpl<>(List.of()));

        assertThat(service.getFeed(PageRequest.of(0, 20)).content()).isEmpty();
        verify(postReactionRepository, never()).findLikedPostIds(any(), anyCollection());
    }

    @Test
    void communityFeedUsesTheVisibilityFilteredQuery() {
        UUID communityId = UUID.randomUUID();
        var pageable = PageRequest.of(0, 20);
        when(postRepository.findVisibleByCommunity(eq(communityId), eq(USER_ID),
            anyCollection(), anyCollection(), eq(pageable))).thenReturn(new PageImpl<>(List.of()));

        assertThat(service.getCommunityFeed(communityId, pageable).content()).isEmpty();

        verify(postRepository).findVisibleByCommunity(eq(communityId), eq(USER_ID),
            anyCollection(), anyCollection(), eq(pageable));
    }

    @Test
    void repostHistoryUsesTheVisibilityFilteredQuery() {
        var pageable = PageRequest.of(0, 20);
        when(postRepostRepository.findVisibleByUserId(eq(USER_ID), anyCollection(),
            anyCollection(), eq(pageable))).thenReturn(new PageImpl<>(List.of()));

        assertThat(service.getReposts(pageable).content()).isEmpty();

        verify(postRepostRepository).findVisibleByUserId(eq(USER_ID), anyCollection(),
            anyCollection(), eq(pageable));
    }

    @Test
    void likeIsIdempotentAndDoesNotDoubleCount() {
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));
        when(postReactionRepository.findByPostIdAndUserId(POST_ID, USER_ID))
            .thenReturn(Optional.of(PostReaction.builder().build()));

        int count = service.like(POST_ID);

        assertThat(count).isEqualTo(4);
        verify(postRepository).findByIdAndDeletedAtIsNullForUpdate(POST_ID);
        verify(postReactionRepository, never()).save(any(PostReaction.class));
        verify(postRepository, never()).adjustLikeCount(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void likeCreatesTheReactionAndIncrementsTheCount() {
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));
        when(postReactionRepository.findByPostIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.empty());

        int count = service.like(POST_ID);

        assertThat(count).isEqualTo(5);
        verify(postRepository).findByIdAndDeletedAtIsNullForUpdate(POST_ID);
        verify(postRepository).adjustLikeCount(POST_ID, 1);
    }

    @Test
    void likingARestrictedPostRequiresVisibilityBeforeWritingReaction() {
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));
        doThrow(new NotFoundException("Post not found"))
            .when(postVisibilityService).requireCanView(post, USER_ID);

        assertThatThrownBy(() -> service.like(POST_ID)).isInstanceOf(NotFoundException.class);

        verify(postReactionRepository, never()).save(any(PostReaction.class));
        verify(postRepository, never()).adjustLikeCount(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void repostCreatesOneRecordAndIncrementsTheShareCount() {
        UUID originalAuthorId = UUID.randomUUID();
        User originalAuthor = new User();
        originalAuthor.setFullName("Original Author");
        ReflectionTestUtils.setField(originalAuthor, "id", originalAuthorId);
        post.setUser(originalAuthor);
        post.setShareCount(2);

        PostRepost savedRepost = PostRepost.builder()
            .post(post)
            .user(author)
            .caption("Worth reading")
            .build();
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));
        when(postRepostRepository.findByPostIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.empty());
        when(postRepostRepository.save(any(PostRepost.class))).thenReturn(savedRepost);

        PostResponse response = service.repost(POST_ID, new RepostRequest(" Worth reading "));

        assertThat(response.shareCount()).isEqualTo(3);
        assertThat(response.repostedByMe()).isTrue();
        assertThat(response.repostCaption()).isEqualTo("Worth reading");
        assertThat(response.reposterId()).isEqualTo(USER_ID);
        verify(postRepository).adjustShareCount(POST_ID, 1);
    }

    @Test
    void repostRequiresVisibilityBeforeWritingShare() {
        UUID otherAuthorId = UUID.randomUUID();
        User otherAuthor = new User();
        ReflectionTestUtils.setField(otherAuthor, "id", otherAuthorId);
        post.setUser(otherAuthor);
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));
        doThrow(new NotFoundException("Post not found"))
            .when(postVisibilityService).requireCanView(post, USER_ID);

        assertThatThrownBy(() -> service.repost(POST_ID, new RepostRequest(null)))
            .isInstanceOf(NotFoundException.class);

        verify(postRepostRepository, never()).save(any(PostRepost.class));
    }

    @Test
    void unrepostIsIdempotentAndDecrementsOnlyWhenARecordWasRemoved() {
        post.setShareCount(3);
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));
        when(postRepostRepository.deleteByPostIdAndUserId(POST_ID, USER_ID)).thenReturn(1);

        PostResponse response = service.unrepost(POST_ID);

        assertThat(response.shareCount()).isEqualTo(2);
        assertThat(response.repostedByMe()).isFalse();
        verify(postRepository).adjustShareCount(POST_ID, -1);
    }

    @Test
    void likeByAnotherUserEmitsPostLikeNotification() {
        UUID otherUserId = UUID.randomUUID();
        User otherAuthor = new User();
        otherAuthor.setFullName("Post Author");
        ReflectionTestUtils.setField(otherAuthor, "id", otherUserId);
        post.setUser(otherAuthor);

        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));
        when(postReactionRepository.findByPostIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.empty());

        int count = service.like(POST_ID);

        assertThat(count).isEqualTo(5);
        org.mockito.ArgumentCaptor<NotificationRequest> captor =
            org.mockito.ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationEmitter).emit(captor.capture());
        NotificationRequest req = captor.getValue();
        assertThat(req.recipientUserId()).isEqualTo(otherUserId);
        assertThat(req.templateKey()).isEqualTo("COMMUNITY_POST_LIKE");
        assertThat(req.referenceType()).isEqualTo("POST");
        assertThat(req.referenceId()).isEqualTo(POST_ID);
        assertThat(req.dedupeKey()).isEqualTo("post-like:" + POST_ID + ":" + USER_ID);
        assertThat(req.channels()).containsExactlyInAnyOrder(
            kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel.IN_APP,
            kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel.MOBILE_PUSH);
    }

    @Test
    void unlikingSomethingNeverLikedIsANoOp() {
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));
        when(postReactionRepository.findByPostIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.empty());

        assertThat(service.unlike(POST_ID)).isEqualTo(4);
        verify(postRepository, never()).adjustLikeCount(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void deleteIsASoftDelete() {
        when(postRepository.findByIdAndUserIdAndDeletedAtIsNull(POST_ID, USER_ID)).thenReturn(Optional.of(post));

        service.delete(POST_ID);

        assertThat(post.getDeletedAt()).isNotNull();
        // Soft delete must release the post's hashtags, or trending overcounts deleted content.
        verify(hashtagService).clearForPost(post);
        verify(communityRepository, never()).adjustPostCount(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void authorDeletionDecrementsCommunityCountOnceAndRepeatedDeleteDoesNotDecrement() {
        Community community = communityWithId();
        post.setCommunity(community);
        when(postRepository.findByIdAndUserIdAndDeletedAtIsNullForUpdate(POST_ID, USER_ID))
            .thenReturn(Optional.of(post), Optional.empty());

        service.delete(POST_ID);
        assertThatThrownBy(() -> service.delete(POST_ID)).isInstanceOf(NotFoundException.class);

        verify(communityRepository, times(1)).adjustPostCount(community.getId(), -1);
    }

    /** Someone else's post reads as not found, so the API does not confirm it exists. */
    @Test
    void deletingSomeoneElsesPostReadsAsNotFound() {
        when(postRepository.findByIdAndUserIdAndDeletedAtIsNullForUpdate(POST_ID, USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(POST_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updatingSomeoneElsesPostReadsAsNotFound() {
        when(postRepository.findByIdAndUserIdAndDeletedAtIsNull(POST_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(POST_ID,
            new PostCreateRequest("hack", null, null, null, null, null)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void moderatorCanRemoveACommunityPostAndTheCountDrops() {
        Community community = communityWithId();
        post.setCommunity(community);
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));
        when(communityMemberRepository.findByCommunityIdAndUserId(community.getId(), USER_ID))
            .thenReturn(Optional.of(CommunityMember.builder()
                .role(CommunityRole.MODERATOR).status(MemberStatus.ACTIVE).build()));

        service.removeAsModerator(POST_ID);

        assertThat(post.getDeletedAt()).isNotNull();
        verify(communityRepository).adjustPostCount(community.getId(), -1);
        verify(hashtagService).clearForPost(post);
    }

    @Test
    void repeatedModeratorDeletionDoesNotDecrementCommunityCountTwice() {
        Community community = communityWithId();
        post.setCommunity(community);
        when(postRepository.findByIdAndDeletedAtIsNullForUpdate(POST_ID))
            .thenReturn(Optional.of(post), Optional.empty());
        when(communityMemberRepository.findByCommunityIdAndUserId(community.getId(), USER_ID))
            .thenReturn(Optional.of(CommunityMember.builder()
                .role(CommunityRole.MODERATOR).status(MemberStatus.ACTIVE).build()));

        service.removeAsModerator(POST_ID);
        assertThatThrownBy(() -> service.removeAsModerator(POST_ID)).isInstanceOf(NotFoundException.class);

        verify(communityRepository, times(1)).adjustPostCount(community.getId(), -1);
    }

    @Test
    void moderatorRemovalNotifiesThePostAuthorWithReferenceAndDedupeData() {
        Community community = communityWithId();
        post.setCommunity(community);
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));
        when(communityMemberRepository.findByCommunityIdAndUserId(community.getId(), USER_ID))
            .thenReturn(Optional.of(CommunityMember.builder()
                .role(CommunityRole.MODERATOR).status(MemberStatus.ACTIVE).build()));

        service.removeAsModerator(POST_ID);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationEmitter).emit(captor.capture());
        NotificationRequest request = captor.getValue();
        assertThat(request.recipientUserId()).isEqualTo(USER_ID);
        assertThat(request.templateKey()).isEqualTo("COMMUNITY_POST_MODERATED");
        assertThat(request.type()).isEqualTo("COMMUNITY_POST_MODERATED");
        assertThat(request.parameters())
            .containsEntry("communityName", "Flutter KH")
            .containsEntry("moderationAction", "removed");
        assertThat(request.referenceType()).isEqualTo("POST");
        assertThat(request.referenceId()).isEqualTo(POST_ID);
        assertThat(request.dedupeKey()).isEqualTo("community-post-moderated:" + POST_ID + ":" + USER_ID);
    }

    @Test
    void notificationWriterFailureDoesNotUndoPostModeration() {
        Community community = communityWithId();
        post.setCommunity(community);
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));
        when(communityMemberRepository.findByCommunityIdAndUserId(community.getId(), USER_ID))
            .thenReturn(Optional.of(CommunityMember.builder()
                .role(CommunityRole.MODERATOR).status(MemberStatus.ACTIVE).build()));
        service = new PostServiceImpl(postRepository, postReactionRepository, postReportRepository,
            postRepostRepository, savedItemRepository, communityRepository, communityMemberRepository, followRepository,
            hashtagService, userRepository, currentUserProvider,
            pollRepository, pollOptionRepository, pollVoteRepository, emitterWithWriterFailure(),
            postVisibilityService, mediaService);

        service.removeAsModerator(POST_ID);

        assertThat(post.getDeletedAt()).isNotNull();
        verify(postRepository).save(post);
        verify(communityRepository).adjustPostCount(community.getId(), -1);
        verify(hashtagService).clearForPost(post);
        verify(notificationWriter).writeInNewTransaction(any(NotificationRequestedEvent.class));
    }

    /** Moderation is a role power, so a plain member is refused - and as not-found, not 403. */
    @Test
    void aPlainMemberCannotRemoveAPostAndGetsNotFound() {
        Community community = communityWithId();
        post.setCommunity(community);
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));
        when(communityMemberRepository.findByCommunityIdAndUserId(community.getId(), USER_ID))
            .thenReturn(Optional.of(CommunityMember.builder()
                .role(CommunityRole.MEMBER).status(MemberStatus.ACTIVE).build()));

        assertThatThrownBy(() -> service.removeAsModerator(POST_ID)).isInstanceOf(NotFoundException.class);
        assertThat(post.getDeletedAt()).isNull();
        verify(communityRepository, never()).adjustPostCount(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(notificationEmitter, never()).emit(any(NotificationRequest.class));
    }

    @Test
    void aNonMemberCannotModerateAndGetsNotFound() {
        Community community = communityWithId();
        post.setCommunity(community);
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));
        when(communityMemberRepository.findByCommunityIdAndUserId(community.getId(), USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeAsModerator(POST_ID)).isInstanceOf(NotFoundException.class);
        assertThat(post.getDeletedAt()).isNull();
        verify(notificationEmitter, never()).emit(any(NotificationRequest.class));
    }

    /** A post outside any community has no moderators, so there is nothing to authorise against. */
    @Test
    void moderatingAPostWithNoCommunityIsNotFound() {
        post.setCommunity(null);
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.removeAsModerator(POST_ID)).isInstanceOf(NotFoundException.class);
    }

    private NotificationEmitter emitterWithWriterFailure() {
        NotificationEventListener listener = new NotificationEventListener(notificationWriter);
        doThrow(new IllegalStateException("notification store unavailable"))
            .when(notificationWriter).writeInNewTransaction(any(NotificationRequestedEvent.class));
        return request -> listener.onNotificationRequested(new NotificationRequestedEvent(request));
    }

    // ---- Reporting & moderation queue ----

    @Test
    void reportingAPostRecordsItAndBumpsTheCount() {
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));
        when(postReportRepository.existsByPostIdAndUserId(POST_ID, USER_ID)).thenReturn(false);

        service.report(POST_ID, new PostReportRequest("SPAM", "buy now"));

        verify(postReportRepository).save(any(PostReport.class));
        verify(postRepository).adjustReportCount(POST_ID, 1);
    }

    /** One report per user, so the count means distinct reporters, not taps. */
    @Test
    void reportingTheSamePostTwiceByTheSameUserIsANoOp() {
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));
        when(postReportRepository.existsByPostIdAndUserId(POST_ID, USER_ID)).thenReturn(true);

        service.report(POST_ID, new PostReportRequest("SPAM", null));

        verify(postReportRepository, never()).save(any(PostReport.class));
        verify(postRepository, never()).adjustReportCount(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void theReportedQueueIsModeratorOnly() {
        UUID communityId = UUID.randomUUID();
        when(communityMemberRepository.findByCommunityIdAndUserId(communityId, USER_ID))
            .thenReturn(Optional.of(CommunityMember.builder()
                .role(CommunityRole.MEMBER).status(MemberStatus.ACTIVE).build()));

        assertThatThrownBy(() -> service.reportedPosts(communityId, PageRequest.of(0, 20)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void theReportedQueueReturnsPostsWithTheirReportCountForAModerator() {
        UUID communityId = UUID.randomUUID();
        when(communityMemberRepository.findByCommunityIdAndUserId(communityId, USER_ID))
            .thenReturn(Optional.of(CommunityMember.builder()
                .role(CommunityRole.MODERATOR).status(MemberStatus.ACTIVE).build()));
        post.setCommunity(communityWithId());
        post.setReportCount(3);
        when(postRepository.findReportedByCommunity(eq(communityId), any())).thenReturn(new PageImpl<>(List.of(post)));

        PageResponse<ReportedPostResponse> queue = service.reportedPosts(communityId, PageRequest.of(0, 20));

        assertThat(queue.content()).hasSize(1);
        assertThat(queue.content().get(0).reportCount()).isEqualTo(3);
        assertThat(queue.content().get(0).author().username()).isEqualTo("author");
    }

    @Test
    void dismissingReportsClearsTheRowsAndZeroesTheCount() {
        Community community = communityWithId();
        post.setCommunity(community);
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));
        when(communityMemberRepository.findByCommunityIdAndUserId(community.getId(), USER_ID))
            .thenReturn(Optional.of(CommunityMember.builder()
                .role(CommunityRole.ADMIN).status(MemberStatus.ACTIVE).build()));

        service.dismissReports(POST_ID);

        verify(postReportRepository).deleteByPostId(POST_ID);
        verify(postRepository).clearReportCount(POST_ID);
    }

    @Test
    void dismissingReportsIsModeratorOnly() {
        Community community = communityWithId();
        post.setCommunity(community);
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));
        when(communityMemberRepository.findByCommunityIdAndUserId(community.getId(), USER_ID))
            .thenReturn(Optional.of(CommunityMember.builder()
                .role(CommunityRole.MEMBER).status(MemberStatus.ACTIVE).build()));

        assertThatThrownBy(() -> service.dismissReports(POST_ID)).isInstanceOf(NotFoundException.class);
        verify(postReportRepository, never()).deleteByPostId(any());
    }

    // ---- Polls ----

    @Test
    void createPollPostPersistsThePollAndItsOptions() {
        when(pollRepository.save(any(Poll.class))).thenAnswer(invocation -> {
            Poll saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            }
            return saved;
        });

        PostResponse response = service.createPollPost(new PollCreateRequest(
            "Pick one", "Best language?", List.of("Java", "Kotlin", "Dart"),
            Visibility.PUBLIC, Language.ENGLISH, null, 7, null, null, null,
            null, null, null, null, null, null));

        var captor = org.mockito.ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getPostType()).isEqualTo(PostType.POLL);
        var pollCaptor = org.mockito.ArgumentCaptor.forClass(Poll.class);
        verify(pollRepository).save(pollCaptor.capture());
        verify(pollOptionRepository, times(3)).save(any(PollOption.class));
        assertThat(response.postType()).isEqualTo(PostType.POLL);
        assertThat(pollCaptor.getValue().getDurationDays()).isEqualTo(7);
        assertThat(pollCaptor.getValue().getExpiresAt()).isNotNull();
    }

    @Test
    void feedPollsKeepTheirOwnPostPollAndOptionIdentities() {
        Post secondPost = Post.builder().user(author).contentText("Poll B")
            .postType(PostType.POLL).visibility(Visibility.PUBLIC)
            .likeCount(0).commentCount(0).shareCount(0).saveCount(0).reportCount(0).build();
        UUID secondPostId = UUID.randomUUID();
        ReflectionTestUtils.setField(secondPost, "id", secondPostId);
        post.setPostType(PostType.POLL);
        post.setContentText("Poll A");
        when(postRepository.findVisibleFeed(eq(USER_ID), anyCollection(), anyCollection(), any()))
            .thenReturn(new PageImpl<>(List.of(post, secondPost)));

        UUID pollAId = UUID.randomUUID();
        UUID pollBId = UUID.randomUUID();
        Poll pollA = Poll.builder().post(post).question("Which feature do you like?").build();
        Poll pollB = Poll.builder().post(secondPost).question("Which platform do you use?").build();
        ReflectionTestUtils.setField(pollA, "id", pollAId);
        ReflectionTestUtils.setField(pollB, "id", pollBId);
        when(pollRepository.findByPostIdIn(anyCollection())).thenReturn(List.of(pollA, pollB));

        UUID optionAId = UUID.randomUUID();
        UUID optionBId = UUID.randomUUID();
        PollOption optionA = PollOption.builder().poll(pollA).optionText("Community").voteCount(2).build();
        PollOption optionB = PollOption.builder().poll(pollB).optionText("Telegram").voteCount(3).build();
        ReflectionTestUtils.setField(optionA, "id", optionAId);
        ReflectionTestUtils.setField(optionB, "id", optionBId);
        when(pollOptionRepository.findByPollIdInOrderByCreatedAtAsc(anyCollection()))
            .thenReturn(List.of(optionA, optionB));
        when(pollVoteRepository.findByPollIdInAndUserId(anyCollection(), eq(USER_ID)))
            .thenReturn(List.of());

        var feed = service.getFeed(PageRequest.of(0, 20));

        assertThat(feed.content()).hasSize(2);
        PostResponse responseA = feed.content().get(0);
        PostResponse responseB = feed.content().get(1);
        assertThat(responseA.postId()).isEqualTo(POST_ID);
        assertThat(responseA.poll().pollId()).isEqualTo(pollAId);
        assertThat(responseA.poll().question()).isEqualTo("Which feature do you like?");
        assertThat(responseA.poll().options()).extracting("optionId").containsExactly(optionAId);
        assertThat(responseB.postId()).isEqualTo(secondPostId);
        assertThat(responseB.poll().pollId()).isEqualTo(pollBId);
        assertThat(responseB.poll().question()).isEqualTo("Which platform do you use?");
        assertThat(responseB.poll().options()).extracting("optionId").containsExactly(optionBId);
    }

    @Test
    void votingOnOnePollRejectsAnOptionOwnedByAnotherPoll() {
        post.setPostType(PostType.POLL);
        when(postRepository.findByIdAndDeletedAtIsNullForUpdate(POST_ID)).thenReturn(Optional.of(post));
        UUID pollAId = UUID.randomUUID();
        UUID pollBId = UUID.randomUUID();
        Poll pollA = Poll.builder().post(post).question("Poll A").build();
        ReflectionTestUtils.setField(pollA, "id", pollAId);
        when(pollRepository.findByPostId(POST_ID)).thenReturn(Optional.of(pollA));
        UUID optionFromPollB = UUID.randomUUID();
        when(pollOptionRepository.findByIdAndPollId(optionFromPollB, pollAId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.vote(POST_ID, new PollVoteRequest(optionFromPollB)))
            .isInstanceOf(NotFoundException.class);

        verify(pollVoteRepository, never()).save(any(PollVote.class));
        verify(pollOptionRepository, never()).adjustVoteCount(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void votingOnANonPollPostIsRejected() {
        post.setPostType(PostType.TEXT);
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.vote(POST_ID, new PollVoteRequest(UUID.randomUUID())))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void pollVotingRequiresVisibilityBeforeReadingOrWritingVotes() {
        post.setPostType(PostType.POLL);
        doThrow(new NotFoundException("Post not found"))
            .when(postVisibilityService).requireCanView(post, USER_ID);

        assertThatThrownBy(() -> service.vote(POST_ID, new PollVoteRequest(UUID.randomUUID())))
            .isInstanceOf(NotFoundException.class);

        verify(pollRepository, never()).findByPostId(POST_ID);
        verify(pollVoteRepository, never()).save(any(PollVote.class));
        verify(postRepository).findByIdAndDeletedAtIsNullForUpdate(POST_ID);
    }

    @Test
    void firstVoteIsRecordedAndTheTallyGoesUp() {
        PollFixture fixture = pollFixture();
        when(pollVoteRepository.findByPollIdAndUserId(fixture.pollId(), USER_ID)).thenReturn(Optional.empty());

        service.vote(POST_ID, new PollVoteRequest(fixture.optionAId()));

        verify(pollVoteRepository).save(any(PollVote.class));
        verify(pollOptionRepository).adjustVoteCount(fixture.optionAId(), 1);
    }

    @Test
    void changingVoteMovesTheTallyBetweenOptions() {
        PollFixture fixture = pollFixture();
        PollVote existing = PollVote.builder().poll(fixture.poll()).option(fixture.optionB()).user(author).build();
        when(pollVoteRepository.findByPollIdAndUserId(fixture.pollId(), USER_ID)).thenReturn(Optional.of(existing));

        service.vote(POST_ID, new PollVoteRequest(fixture.optionAId()));

        assertThat(existing.getOption()).isEqualTo(fixture.optionA());
        verify(pollOptionRepository).adjustVoteCount(fixture.optionBId(), -1);
        verify(pollOptionRepository).adjustVoteCount(fixture.optionAId(), 1);
        verify(pollVoteRepository).save(existing);
        verify(postRepository).findByIdAndDeletedAtIsNullForUpdate(POST_ID);
    }

    @Test
    void reVotingTheSameOptionIsANoOp() {
        PollFixture fixture = pollFixture();
        PollVote existing = PollVote.builder().poll(fixture.poll()).option(fixture.optionA()).user(author).build();
        when(pollVoteRepository.findByPollIdAndUserId(fixture.pollId(), USER_ID)).thenReturn(Optional.of(existing));

        service.vote(POST_ID, new PollVoteRequest(fixture.optionAId()));

        verify(pollVoteRepository, never()).save(any(PollVote.class));
        verify(pollOptionRepository, never()).adjustVoteCount(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    /** Wires up a POLL post with a poll and two options (A, B), all stubbed for voting. */
    private PollFixture pollFixture() {
        post.setPostType(PostType.POLL);
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));

        UUID pollId = UUID.randomUUID();
        Poll poll = Poll.builder().post(post).question("Best language?").build();
        ReflectionTestUtils.setField(poll, "id", pollId);
        when(pollRepository.findByPostId(POST_ID)).thenReturn(Optional.of(poll));

        UUID optionAId = UUID.randomUUID();
        UUID optionBId = UUID.randomUUID();
        PollOption optionA = PollOption.builder().poll(poll).optionText("A").voteCount(0).build();
        ReflectionTestUtils.setField(optionA, "id", optionAId);
        PollOption optionB = PollOption.builder().poll(poll).optionText("B").voteCount(0).build();
        ReflectionTestUtils.setField(optionB, "id", optionBId);
        when(pollOptionRepository.findByIdAndPollId(optionAId, pollId)).thenReturn(Optional.of(optionA));
        when(pollOptionRepository.findByIdAndPollId(optionBId, pollId)).thenReturn(Optional.of(optionB));

        return new PollFixture(pollId, poll, optionAId, optionA, optionBId, optionB);
    }

    private record PollFixture(UUID pollId, Poll poll, UUID optionAId, PollOption optionA,
                               UUID optionBId, PollOption optionB) {}

    private Community communityWithId() {
        Community community = Community.builder().name("Flutter KH").slug("flutter-kh")
            .memberCount(1).postCount(3).build();
        ReflectionTestUtils.setField(community, "id", UUID.randomUUID());
        return community;
    }
}
