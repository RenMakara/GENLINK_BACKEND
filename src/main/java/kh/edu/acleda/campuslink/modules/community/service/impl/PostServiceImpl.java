package kh.edu.acleda.campuslink.modules.community.service.impl;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kh.edu.acleda.campuslink.modules.community.dto.request.PostCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse;
import kh.edu.acleda.campuslink.modules.community.entity.Community;
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
import kh.edu.acleda.campuslink.modules.community.service.PostService;
import kh.edu.acleda.campuslink.modules.profile.enums.SavedItemType;
import kh.edu.acleda.campuslink.modules.profile.repository.SavedItemRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostServiceImpl implements PostService {

    private static final String DEFAULT_REACTION = "LIKE";
    /** Stands in for an empty IN list, which is invalid SQL. Matches nothing. */
    private static final UUID PLACEHOLDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final PostRepository postRepository;
    private final PostReactionRepository postReactionRepository;
    private final SavedItemRepository savedItemRepository;
    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final FollowRepository followRepository;
    private final HashtagService hashtagService;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public PostServiceImpl(PostRepository postRepository,
                           PostReactionRepository postReactionRepository,
                           SavedItemRepository savedItemRepository,
                           CommunityRepository communityRepository,
                           CommunityMemberRepository communityMemberRepository,
                           FollowRepository followRepository,
                           HashtagService hashtagService,
                           UserRepository userRepository,
                           CurrentUserProvider currentUserProvider) {
        this.postRepository = postRepository;
        this.postReactionRepository = postReactionRepository;
        this.savedItemRepository = savedItemRepository;
        this.communityRepository = communityRepository;
        this.communityMemberRepository = communityMemberRepository;
        this.followRepository = followRepository;
        this.hashtagService = hashtagService;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public PostResponse create(PostCreateRequest request) {
        User author = loadCurrentUser();
        Community community = resolveCommunity(request.communityId());

        Post post = Post.builder()
            .user(author)
            .community(community)
            .contentText(request.contentText())
            .imageUrl(request.imageUrl())
            .postType(request.postType() == null ? PostType.TEXT : request.postType())
            .visibility(request.visibility() == null ? Visibility.PUBLIC : request.visibility())
            .language(request.language())
            .likeCount(0)
            .commentCount(0)
            .shareCount(0)
            .saveCount(0)
            .build();

        Post saved = postRepository.save(post);
        if (community != null) {
            communityRepository.adjustPostCount(community.getId(), 1);
        }
        hashtagService.syncForPost(saved, saved.getContentText());
        return toResponse(saved, false, false, hashtagsFor(saved.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PostResponse> getFeed(Pageable pageable) {
        UUID viewerId = currentUserProvider.getCurrentUserId();
        return hydratePage(postRepository.findVisibleFeed(
            viewerId, orPlaceholder(followRepository.findFriendIds(viewerId)),
            orPlaceholder(communityMemberRepository.findActiveCommunityIds(viewerId)), pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PostResponse> getCommunityFeed(UUID communityId, Pageable pageable) {
        return hydratePage(postRepository.findByCommunity(communityId, pageable));
    }

    /**
     * "IN ()" is invalid SQL, so an empty list becomes a placeholder that matches
     * nothing. A user with no friends and no communities still gets the public feed.
     */
    private Collection<UUID> orPlaceholder(List<UUID> ids) {
        return ids.isEmpty() ? List.of(PLACEHOLDER_ID) : ids;
    }

    /** Posting into a community you are not an active member of is not allowed. */
    private Community resolveCommunity(UUID communityId) {
        if (communityId == null) {
            return null;
        }
        Community community = communityRepository.findByIdAndDeletedAtIsNull(communityId)
            .orElseThrow(() -> new NotFoundException("Community not found"));
        boolean member = communityMemberRepository
            .findActiveCommunityIds(currentUserProvider.getCurrentUserId())
            .contains(communityId);
        if (!member) {
            throw new NotFoundException("Community not found");
        }
        return community;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PostResponse> getMine(Pageable pageable) {
        return hydratePage(postRepository.findByAuthor(currentUserProvider.getCurrentUserId(), pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponse getById(UUID id) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("Post not found"));
        UUID viewerId = currentUserProvider.getCurrentUserId();
        return toResponse(post,
            postReactionRepository.findByPostIdAndUserId(id, viewerId).isPresent(),
            savedItemRepository.existsByUserIdAndItemTypeAndItemId(viewerId, SavedItemType.POST, id),
            hashtagsFor(id));
    }

    @Override
    @Transactional
    public PostResponse update(UUID id, PostCreateRequest request) {
        Post post = loadOwned(id);
        post.setContentText(request.contentText());
        post.setImageUrl(request.imageUrl());
        if (request.postType() != null) {
            post.setPostType(request.postType());
        }
        if (request.visibility() != null) {
            post.setVisibility(request.visibility());
        }
        if (request.language() != null) {
            post.setLanguage(request.language());
        }

        Post saved = postRepository.save(post);
        // Editing the text can add or remove tags, so links are rebuilt, not appended.
        hashtagService.syncForPost(saved, saved.getContentText());
        return getById(saved.getId());
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Post post = loadOwned(id);
        post.setDeletedAt(Instant.now());
        postRepository.save(post);
    }

    @Override
    @Transactional
    public int like(UUID postId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
            .orElseThrow(() -> new NotFoundException("Post not found"));
        UUID userId = currentUserProvider.getCurrentUserId();

        // post_reactions has UNIQUE(post_id, user_id), so a double tap must no-op
        // rather than 500 - and the count must not drift on the second tap.
        if (postReactionRepository.findByPostIdAndUserId(postId, userId).isPresent()) {
            return post.getLikeCount();
        }

        postReactionRepository.save(PostReaction.builder()
            .post(post)
            .user(loadCurrentUser())
            .reactionType(DEFAULT_REACTION)
            .build());
        postRepository.adjustLikeCount(postId, 1);
        return post.getLikeCount() + 1;
    }

    @Override
    @Transactional
    public int unlike(UUID postId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
            .orElseThrow(() -> new NotFoundException("Post not found"));
        UUID userId = currentUserProvider.getCurrentUserId();

        return postReactionRepository.findByPostIdAndUserId(postId, userId)
            .map(reaction -> {
                postReactionRepository.delete(reaction);
                postRepository.adjustLikeCount(postId, -1);
                return post.getLikeCount() - 1;
            })
            .orElseGet(post::getLikeCount);
    }

    /**
     * Resolves likedByMe, savedByMe and hashtags for the whole page in three queries
     * rather than three per row. Without this the feed is an N+1 machine.
     */
    private PageResponse<PostResponse> hydratePage(Page<Post> page) {
        List<Post> posts = page.getContent();
        if (posts.isEmpty()) {
            return PageResponse.from(page, post -> toResponse(post, false, false, List.of()));
        }

        UUID viewerId = currentUserProvider.getCurrentUserId();
        Set<UUID> postIds = posts.stream().map(Post::getId).collect(Collectors.toSet());
        Set<UUID> liked = new HashSet<>(postReactionRepository.findLikedPostIds(viewerId, postIds));
        Set<UUID> saved = new HashSet<>(
            savedItemRepository.findSavedItemIds(viewerId, SavedItemType.POST, postIds));
        Map<UUID, List<String>> tags = hashtagService.tagsForPosts(postIds);

        return PageResponse.from(page, post -> toResponse(
            post,
            liked.contains(post.getId()),
            saved.contains(post.getId()),
            tags.getOrDefault(post.getId(), List.of())));
    }

    private List<String> hashtagsFor(UUID postId) {
        return hashtagService.tagsForPosts(Set.of(postId)).getOrDefault(postId, List.of());
    }

    /** Owner-scoped: another user's id reads as not found, never as forbidden. */
    private Post loadOwned(UUID id) {
        return postRepository.findByIdAndUserIdAndDeletedAtIsNull(id, currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("Post not found"));
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private PostResponse toResponse(Post post, boolean likedByMe, boolean savedByMe, List<String> hashtags) {
        User author = post.getUser();
        return new PostResponse(
            post.getId(),
            author == null ? null : new PostResponse.AuthorSummary(
                author.getId(), author.getFullName(), author.getUsername(), author.getAvatarUrl()),
            post.getContentText(),
            post.getImageUrl(),
            post.getPostType(),
            post.getVisibility(),
            post.getLanguage(),
            post.getLikeCount(),
            post.getCommentCount(),
            likedByMe,
            savedByMe,
            hashtags,
            post.getCreatedAt()
        );
    }
}
