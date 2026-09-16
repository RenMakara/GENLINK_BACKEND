package kh.edu.acleda.campuslink.modules.community.service.impl;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kh.edu.acleda.campuslink.modules.community.dto.request.PostCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.PostReportRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.ReportedPostResponse;
import kh.edu.acleda.campuslink.modules.community.entity.Community;
import kh.edu.acleda.campuslink.modules.community.entity.CommunityMember;
import kh.edu.acleda.campuslink.modules.community.entity.Post;
import kh.edu.acleda.campuslink.modules.community.entity.PostReaction;
import kh.edu.acleda.campuslink.modules.community.entity.PostReport;
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
import kh.edu.acleda.campuslink.modules.community.service.PostService;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.profile.enums.SavedItemType;
import kh.edu.acleda.campuslink.modules.profile.repository.SavedItemRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import kh.edu.acleda.campuslink.modules.community.dto.request.PollCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.PollVoteRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.PollResponse;
import kh.edu.acleda.campuslink.modules.community.entity.Poll;
import kh.edu.acleda.campuslink.modules.community.entity.PollOption;
import kh.edu.acleda.campuslink.modules.community.entity.PollVote;
import kh.edu.acleda.campuslink.modules.community.repository.PollRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PollOptionRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PollVoteRepository;
import kh.edu.acleda.campuslink.shared.exception.ApiException;

@Slf4j
@Service
public class PostServiceImpl implements PostService {

    private static final String DEFAULT_REACTION = "LIKE";
    /** Stands in for an empty IN list, which is invalid SQL. Matches nothing. */
    private static final UUID PLACEHOLDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    /** Community roles allowed to remove another member's post. */
    private static final Set<CommunityRole> MODERATOR_ROLES =
        EnumSet.of(CommunityRole.OWNER, CommunityRole.ADMIN, CommunityRole.MODERATOR);
    private static final String POST_MODERATION_NOTIFICATION_TEMPLATE = "COMMUNITY_POST_MODERATED";
    private static final String POST_MODERATION_NOTIFICATION_TYPE = "COMMUNITY_POST_MODERATED";
    private static final String POST_MODERATION_ACTION_REMOVED = "removed";

    private final PostRepository postRepository;
    private final PostReactionRepository postReactionRepository;
    private final PostReportRepository postReportRepository;
    private final SavedItemRepository savedItemRepository;
    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final FollowRepository followRepository;
    private final HashtagService hashtagService;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final PollRepository pollRepository;
    private final PollOptionRepository pollOptionRepository;
    private final PollVoteRepository pollVoteRepository;
    private final NotificationEmitter notificationEmitter;

    public PostServiceImpl(PostRepository postRepository,
                           PostReactionRepository postReactionRepository,
                           PostReportRepository postReportRepository,
                           SavedItemRepository savedItemRepository,
                           CommunityRepository communityRepository,
                           CommunityMemberRepository communityMemberRepository,
                           FollowRepository followRepository,
                           HashtagService hashtagService,
                           UserRepository userRepository,
                           CurrentUserProvider currentUserProvider,
                           PollRepository pollRepository,
                           PollOptionRepository pollOptionRepository,
                           PollVoteRepository pollVoteRepository,
                           NotificationEmitter notificationEmitter) {
        this.postRepository = postRepository;
        this.postReactionRepository = postReactionRepository;
        this.postReportRepository = postReportRepository;
        this.savedItemRepository = savedItemRepository;
        this.communityRepository = communityRepository;
        this.communityMemberRepository = communityMemberRepository;
        this.followRepository = followRepository;
        this.hashtagService = hashtagService;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.pollRepository = pollRepository;
        this.pollOptionRepository = pollOptionRepository;
        this.pollVoteRepository = pollVoteRepository;
        this.notificationEmitter = notificationEmitter;
    }

    @Override
    @Transactional
    public PostResponse create(PostCreateRequest request) {
        User author = loadCurrentUser();
        Community community = resolveCommunity(request.communityId());

        PostType resolvedType = request.postType();
        if (resolvedType == null) {
            if (request.videoUrl() != null && !request.videoUrl().isBlank()) {
                resolvedType = PostType.VIDEO;
            } else if (request.imageUrl() != null && !request.imageUrl().isBlank()) {
                resolvedType = PostType.IMAGE;
            } else {
                resolvedType = PostType.TEXT;
            }
        }

        String videoUrl = request.videoUrl();
        if (videoUrl == null && resolvedType == PostType.VIDEO) {
            videoUrl = request.imageUrl();
        }

        Post post = Post.builder()
            .user(author)
            .community(community)
            .contentText(request.contentText())
            .imageUrl(request.imageUrl())
            .videoUrl(videoUrl)
            .postType(resolvedType)
            .visibility(request.visibility() == null ? Visibility.PUBLIC : request.visibility())
            .language(request.language())
            .likeCount(0)
            .commentCount(0)
            .shareCount(0)
            .saveCount(0)
            .reportCount(0)
            .build();

        Post saved = postRepository.save(post);
        if (community != null) {
            communityRepository.adjustPostCount(community.getId(), 1);
        }
        if (saved.getContentText() != null && !saved.getContentText().isBlank()) {
            hashtagService.syncForPost(saved, saved.getContentText());
        }
        return toResponse(saved, false, false, hashtagsFor(saved.getId()));
    }

    @Override
    @Transactional
    public PostResponse createPollPost(PollCreateRequest request) {
        User author = loadCurrentUser();
        Community community = resolveCommunity(request.communityId());

        Post post = Post.builder()
            .user(author)
            .community(community)
            .contentText(request.caption())
            .postType(PostType.POLL)
            .visibility(request.visibility() == null ? Visibility.PUBLIC : request.visibility())
            .language(request.language())
            .likeCount(0)
            .commentCount(0)
            .shareCount(0)
            .saveCount(0)
            .reportCount(0)
            .build();
        Post saved = postRepository.save(post);
        if (community != null) {
            communityRepository.adjustPostCount(community.getId(), 1);
        }
        // The caption is optional, so only sync tags when there is text to scan.
        if (saved.getContentText() != null && !saved.getContentText().isBlank()) {
            hashtagService.syncForPost(saved, saved.getContentText());
        }

        Poll poll = pollRepository.save(Poll.builder().post(saved).question(request.question()).build());
        for (String optionText : request.options()) {
            pollOptionRepository.save(PollOption.builder()
                .poll(poll)
                .optionText(optionText)
                .voteCount(0)
                .build());
        }
        log.info("Poll post created: post={} options={} by user={}",
            saved.getId(), request.options().size(), author.getId());
        return toResponse(saved, false, false, hashtagsFor(saved.getId()),
            pollsForPosts(Set.of(saved.getId()), author.getId()).get(saved.getId()));
    }

    @Override
    @Transactional
    public PollResponse vote(UUID postId, PollVoteRequest request) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
            .orElseThrow(() -> new NotFoundException("Post not found"));
        if (post.getPostType() != PostType.POLL) {
            throw new ApiException("This post is not a poll", "NOT_A_POLL");
        }
        Poll poll = pollRepository.findByPostId(postId)
            .orElseThrow(() -> new NotFoundException("Poll not found"));
        PollOption option = pollOptionRepository.findByIdAndPollId(request.optionId(), poll.getId())
            .orElseThrow(() -> new NotFoundException("Poll option not found"));
        UUID userId = currentUserProvider.getCurrentUserId();

        PollVote existing = pollVoteRepository.findByPollIdAndUserId(poll.getId(), userId).orElse(null);
        if (existing == null) {
            pollVoteRepository.save(PollVote.builder()
                .poll(poll)
                .option(option)
                .user(loadCurrentUser())
                .votedAt(LocalDateTime.now())
                .build());
            pollOptionRepository.adjustVoteCount(option.getId(), 1);
        } else if (!existing.getOption().getId().equals(option.getId())) {
            // Move the vote: drop the old tally, point the row at the new option, add the new tally.
            UUID previousOptionId = existing.getOption().getId();
            existing.setOption(option);
            existing.setVotedAt(LocalDateTime.now());
            pollVoteRepository.save(existing);
            pollOptionRepository.adjustVoteCount(previousOptionId, -1);
            pollOptionRepository.adjustVoteCount(option.getId(), 1);
        }
        // Re-voting the same option is a deliberate no-op.
        return pollsForPosts(Set.of(postId), userId).get(postId);
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
        PollResponse poll = post.getPostType() == PostType.POLL
            ? pollsForPosts(Set.of(id), viewerId).get(id) : null;
        return toResponse(post,
            postReactionRepository.findByPostIdAndUserId(id, viewerId).isPresent(),
            savedItemRepository.existsByUserIdAndItemTypeAndItemId(viewerId, SavedItemType.POST, id),
            hashtagsFor(id),
            poll);
    }

    @Override
    @Transactional
    public PostResponse update(UUID id, PostCreateRequest request) {
        Post post = loadOwned(id);
        post.setContentText(request.contentText());
        post.setImageUrl(request.imageUrl());
        if (request.videoUrl() != null) {
            post.setVideoUrl(request.videoUrl());
        }
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
        if (saved.getContentText() != null && !saved.getContentText().isBlank()) {
            hashtagService.syncForPost(saved, saved.getContentText());
        } else {
            hashtagService.clearForPost(saved);
        }
        return getById(saved.getId());
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Post post = loadOwned(id);
        post.setDeletedAt(Instant.now());
        postRepository.save(post);
        // Release the post's hashtags too, or their usage counts keep trending pointing at
        // content nobody can see.
        hashtagService.clearForPost(post);
    }

    @Override
    @Transactional
    public void removeAsModerator(UUID postId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
            .orElseThrow(() -> new NotFoundException("Post not found"));
        Community community = post.getCommunity();
        CommunityMember membership = requireModeratorOfPostCommunity(community);

        post.setDeletedAt(Instant.now());
        postRepository.save(post);
        // create() incremented postCount, so a removal decrements it to keep the count honest.
        communityRepository.adjustPostCount(community.getId(), -1);
        // Same reason as the author-delete path: stop the removed post's tags counting in trending.
        hashtagService.clearForPost(post);
        emitPostModerationNotification(post, community);
        log.info("Post {} removed by moderator {} ({}) in community {}",
            postId, currentUserProvider.getCurrentUserId(), membership.getRole(), community.getId());
    }

    @Override
    @Transactional
    public void report(UUID postId, PostReportRequest request) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
            .orElseThrow(() -> new NotFoundException("Post not found"));
        UUID userId = currentUserProvider.getCurrentUserId();

        // One report per user, so report_count means "distinct people flagged this" rather
        // than "how many times someone tapped". The unique (post_id, user_id) backs this.
        if (postReportRepository.existsByPostIdAndUserId(postId, userId)) {
            return;
        }
        postReportRepository.save(PostReport.builder()
            .post(post)
            .user(loadCurrentUser())
            .reason(request.reason())
            .details(request.details())
            .build());
        postRepository.adjustReportCount(postId, 1);
        log.info("Post reported: id={} reason={} by user={}", postId, request.reason(), userId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportedPostResponse> reportedPosts(UUID communityId, Pageable pageable) {
        requireCommunityModerator(communityId);
        return PageResponse.from(
            postRepository.findReportedByCommunity(communityId, pageable),
            this::toReportedResponse);
    }

    @Override
    @Transactional
    public void dismissReports(UUID postId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
            .orElseThrow(() -> new NotFoundException("Post not found"));
        requireModeratorOfPostCommunity(post.getCommunity());

        // Keep the post, drop the flags: clear the rows and zero the cached count so it
        // leaves the queue. A later report from someone new re-opens it.
        postReportRepository.deleteByPostId(postId);
        postRepository.clearReportCount(postId);
        log.info("Reports dismissed on post {} by moderator {}", postId, currentUserProvider.getCurrentUserId());
    }

    /**
     * A post outside any community has no moderators, so there is nothing to authorise
     * against. Otherwise the caller must be an active OWNER/ADMIN/MODERATOR of it. Every
     * refusal is not-found, never forbidden, so the endpoint does not leak the post.
     */
    private CommunityMember requireModeratorOfPostCommunity(Community community) {
        if (community == null) {
            throw new NotFoundException("Post not found");
        }
        CommunityMember membership = communityMemberRepository
            .findByCommunityIdAndUserId(community.getId(), currentUserProvider.getCurrentUserId())
            .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
            .orElseThrow(() -> new NotFoundException("Post not found"));
        if (!MODERATOR_ROLES.contains(membership.getRole())) {
            throw new NotFoundException("Post not found");
        }
        return membership;
    }

    private void emitPostModerationNotification(Post post, Community community) {
        User author = post.getUser();
        if (author == null || author.getId() == null || post.getId() == null || community == null
            || community.getId() == null || community.getName() == null || community.getName().isBlank()) {
            log.warn("Skipping post moderation notification because post details are incomplete: post={}",
                post.getId());
            return;
        }

        notificationEmitter.emit(new NotificationRequest(
            author.getId(),
            POST_MODERATION_NOTIFICATION_TEMPLATE,
            POST_MODERATION_NOTIFICATION_TYPE,
            Map.of(
                "communityName", community.getName(),
                "moderationAction", POST_MODERATION_ACTION_REMOVED),
            "POST",
            post.getId(),
            "community-post-moderated:" + post.getId() + ":" + author.getId()));
    }

    /** Caller must be an active OWNER/ADMIN/MODERATOR of the community; not-found otherwise. */
    private void requireCommunityModerator(UUID communityId) {
        CommunityMember membership = communityMemberRepository
            .findByCommunityIdAndUserId(communityId, currentUserProvider.getCurrentUserId())
            .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
            .orElseThrow(() -> new NotFoundException("Community not found"));
        if (!MODERATOR_ROLES.contains(membership.getRole())) {
            throw new NotFoundException("Community not found");
        }
    }

    private ReportedPostResponse toReportedResponse(Post post) {
        User author = post.getUser();
        return new ReportedPostResponse(
            post.getId(),
            author == null ? null : new PostResponse.AuthorSummary(
                author.getId(), author.getFullName(), author.getUsername(), author.getAvatarUrl()),
            post.getContentText(),
            post.getImageUrl(),
            post.getVideoUrl(),
            post.getReportCount() == null ? 0 : post.getReportCount(),
            post.getCreatedAt());
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
        Map<UUID, PollResponse> polls = pollsForPosts(postIds, viewerId);

        return PageResponse.from(page, post -> toResponse(
            post,
            liked.contains(post.getId()),
            saved.contains(post.getId()),
            tags.getOrDefault(post.getId(), List.of()),
            polls.get(post.getId())));
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
        return toResponse(post, likedByMe, savedByMe, hashtags, null);
    }

    private PostResponse toResponse(Post post, boolean likedByMe, boolean savedByMe,
                                    List<String> hashtags, PollResponse poll) {
        User author = post.getUser();
        return new PostResponse(
            post.getId(),
            author == null ? null : new PostResponse.AuthorSummary(
                author.getId(), author.getFullName(), author.getUsername(), author.getAvatarUrl()),
            post.getContentText(),
            post.getImageUrl(),
            post.getVideoUrl(),
            post.getPostType(),
            post.getVisibility(),
            post.getLanguage(),
            post.getLikeCount(),
            post.getCommentCount(),
            likedByMe,
            savedByMe,
            hashtags,
            poll,
            post.getCreatedAt()
        );
    }

    /**
     * Resolves poll data for a set of posts in three queries (polls, options, the viewer's
     * votes), keyed by post id. Non-poll posts simply have no entry. Keeping it batched is
     * what stops the feed from firing a poll lookup per row.
     */
    private Map<UUID, PollResponse> pollsForPosts(Set<UUID> postIds, UUID viewerId) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        List<Poll> polls = pollRepository.findByPostIdIn(postIds);
        if (polls.isEmpty()) {
            return Map.of();
        }
        Set<UUID> pollIds = polls.stream().map(Poll::getId).collect(Collectors.toSet());
        Map<UUID, List<PollOption>> optionsByPoll = pollOptionRepository
            .findByPollIdInOrderByCreatedAtAsc(pollIds).stream()
            .collect(Collectors.groupingBy(option -> option.getPoll().getId()));
        Map<UUID, UUID> myOptionByPoll = pollVoteRepository.findByPollIdInAndUserId(pollIds, viewerId).stream()
            .collect(Collectors.toMap(vote -> vote.getPoll().getId(), vote -> vote.getOption().getId()));

        Map<UUID, PollResponse> byPost = new HashMap<>();
        for (Poll poll : polls) {
            List<PollOption> options = optionsByPoll.getOrDefault(poll.getId(), List.of());
            int totalVotes = options.stream().mapToInt(this::voteCountOf).sum();
            UUID myOption = myOptionByPoll.get(poll.getId());
            List<PollResponse.Option> optionDtos = options.stream()
                .map(option -> new PollResponse.Option(option.getId(), option.getOptionText(), voteCountOf(option)))
                .toList();
            byPost.put(poll.getPost().getId(), new PollResponse(
                poll.getId(), poll.getQuestion(), totalVotes, myOption != null, myOption, optionDtos));
        }
        return byPost;
    }

    private int voteCountOf(PollOption option) {
        return option.getVoteCount() == null ? 0 : option.getVoteCount();
    }
}
