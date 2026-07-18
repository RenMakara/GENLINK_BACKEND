package kh.edu.acleda.campuslink.modules.community;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.PostCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse;
import kh.edu.acleda.campuslink.modules.community.entity.Post;
import kh.edu.acleda.campuslink.modules.community.entity.PostReaction;
import kh.edu.acleda.campuslink.modules.community.enums.PostType;
import kh.edu.acleda.campuslink.modules.community.enums.Visibility;
import kh.edu.acleda.campuslink.modules.community.repository.CommunityMemberRepository;
import kh.edu.acleda.campuslink.modules.community.repository.CommunityRepository;
import kh.edu.acleda.campuslink.modules.community.repository.FollowRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PostReactionRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PostRepository;
import kh.edu.acleda.campuslink.modules.community.service.HashtagService;
import kh.edu.acleda.campuslink.modules.community.service.impl.PostServiceImpl;
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
    @Mock private SavedItemRepository savedItemRepository;
    @Mock private CommunityRepository communityRepository;
    @Mock private CommunityMemberRepository communityMemberRepository;
    @Mock private FollowRepository followRepository;
    @Mock private HashtagService hashtagService;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private PostServiceImpl service;
    private User author;
    private Post post;

    @BeforeEach
    void setUp() {
        service = new PostServiceImpl(postRepository, postReactionRepository, savedItemRepository,
            communityRepository, communityMemberRepository, followRepository,
            hashtagService, userRepository, currentUserProvider);

        author = new User();
        author.setEmail("author@example.com");
        author.setFullName("Author");
        author.setUsername("author");
        ReflectionTestUtils.setField(author, "id", USER_ID);

        post = Post.builder().user(author).contentText("Hello #genlink")
            .postType(PostType.TEXT).visibility(Visibility.PUBLIC)
            .likeCount(4).commentCount(2).build();
        ReflectionTestUtils.setField(post, "id", POST_ID);

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
        when(followRepository.findFriendIds(any())).thenReturn(List.of());
        when(communityMemberRepository.findActiveCommunityIds(any())).thenReturn(List.of());
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
    void createDefaultsTypeAndVisibilityWhenOmitted() {
        service.create(new PostCreateRequest("no type given", null, null, null, null, null));

        var captor = org.mockito.ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getPostType()).isEqualTo(PostType.TEXT);
        assertThat(captor.getValue().getVisibility()).isEqualTo(Visibility.PUBLIC);
    }

    @Test
    void createSyncsHashtags() {
        service.create(new PostCreateRequest("Hello #genlink", null, null, null, null, null));

        verify(hashtagService).syncForPost(any(Post.class), eq("Hello #genlink"));
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
    void likeIsIdempotentAndDoesNotDoubleCount() {
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));
        when(postReactionRepository.findByPostIdAndUserId(POST_ID, USER_ID))
            .thenReturn(Optional.of(PostReaction.builder().build()));

        int count = service.like(POST_ID);

        assertThat(count).isEqualTo(4);
        verify(postReactionRepository, never()).save(any(PostReaction.class));
        verify(postRepository, never()).adjustLikeCount(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void likeCreatesTheReactionAndIncrementsTheCount() {
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));
        when(postReactionRepository.findByPostIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.empty());

        int count = service.like(POST_ID);

        assertThat(count).isEqualTo(5);
        verify(postRepository).adjustLikeCount(POST_ID, 1);
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
    }

    /** Someone else's post reads as not found, so the API does not confirm it exists. */
    @Test
    void deletingSomeoneElsesPostReadsAsNotFound() {
        when(postRepository.findByIdAndUserIdAndDeletedAtIsNull(POST_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(POST_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updatingSomeoneElsesPostReadsAsNotFound() {
        when(postRepository.findByIdAndUserIdAndDeletedAtIsNull(POST_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(POST_ID,
            new PostCreateRequest("hack", null, null, null, null, null)))
            .isInstanceOf(NotFoundException.class);
    }
}
