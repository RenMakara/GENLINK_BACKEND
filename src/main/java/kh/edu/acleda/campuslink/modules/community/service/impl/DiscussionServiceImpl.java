package kh.edu.acleda.campuslink.modules.community.service.impl;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kh.edu.acleda.campuslink.modules.community.dto.request.CommentCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.DiscussionCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.DiscussionReportRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.RepostRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.CommentResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.DiscussionResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.ReportedDiscussionResponse;
import kh.edu.acleda.campuslink.modules.community.entity.DiscussionReport;
import kh.edu.acleda.campuslink.modules.community.entity.DiscussionRepost;
import kh.edu.acleda.campuslink.modules.community.entity.ForumComment;
import kh.edu.acleda.campuslink.modules.community.entity.ForumCommentReaction;
import kh.edu.acleda.campuslink.modules.community.entity.ForumDiscussion;
import kh.edu.acleda.campuslink.modules.community.entity.ForumReaction;
import kh.edu.acleda.campuslink.modules.community.repository.DiscussionReportRepository;
import kh.edu.acleda.campuslink.modules.community.repository.DiscussionRepostRepository;
import kh.edu.acleda.campuslink.modules.community.repository.ForumCommentReactionRepository;
import kh.edu.acleda.campuslink.modules.community.repository.ForumCommentRepository;
import kh.edu.acleda.campuslink.modules.community.repository.ForumDiscussionRepository;
import kh.edu.acleda.campuslink.modules.community.repository.ForumReactionRepository;
import kh.edu.acleda.campuslink.modules.community.service.DiscussionService;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class DiscussionServiceImpl implements DiscussionService {

    private static final String DEFAULT_REACTION = "LIKE";
    private static final String DISCUSSION_MODERATION_NOTIFICATION_TEMPLATE = "FORUM_DISCUSSION_MODERATED";
    private static final String DISCUSSION_MODERATION_NOTIFICATION_TYPE = "FORUM_DISCUSSION_MODERATED";
    private static final String DISCUSSION_MODERATION_ACTION_REMOVED = "removed";

    private final ForumDiscussionRepository discussionRepository;
    private final ForumCommentRepository commentRepository;
    private final ForumReactionRepository reactionRepository;
    private final ForumCommentReactionRepository commentReactionRepository;
    private final DiscussionReportRepository reportRepository;
    private final DiscussionRepostRepository repostRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final NotificationEmitter notificationEmitter;

    public DiscussionServiceImpl(ForumDiscussionRepository discussionRepository,
                                 ForumCommentRepository commentRepository,
                                 ForumReactionRepository reactionRepository,
                                 ForumCommentReactionRepository commentReactionRepository,
                                 DiscussionReportRepository reportRepository,
                                 DiscussionRepostRepository repostRepository,
                                 UserRepository userRepository,
                                 CurrentUserProvider currentUserProvider,
                                 NotificationEmitter notificationEmitter) {
        this.discussionRepository = discussionRepository;
        this.commentRepository = commentRepository;
        this.reactionRepository = reactionRepository;
        this.commentReactionRepository = commentReactionRepository;
        this.reportRepository = reportRepository;
        this.repostRepository = repostRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.notificationEmitter = notificationEmitter;
    }

    @Override
    @Transactional
    public DiscussionResponse create(DiscussionCreateRequest request) {
        User author = loadCurrentUser();
        // The author is stored even when anonymous: moderation needs to know who wrote
        // it, and the row is the only record. Anonymity is applied when responding.
        ForumDiscussion discussion = discussionRepository.save(ForumDiscussion.builder()
            .user(author)
            .title(request.title().strip())
            .content(request.content())
            .category(request.category())
            .isAnonymous(Boolean.TRUE.equals(request.anonymous()))
            .language(request.language())
            .reactionCount(0)
            .commentCount(0)
            .reportCount(0)
            .build());
        log.info("Discussion created by user {}: {}", author.getId(), request.title());
        return toResponse(discussion, false, author.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DiscussionResponse> browse(String category, Pageable pageable) {
        return hydrate(discussionRepository.findFeed(category, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DiscussionResponse> getMine(Pageable pageable) {
        return hydrate(discussionRepository.findByAuthor(currentUserProvider.getCurrentUserId(), pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DiscussionResponse> getReposts(UUID userId, Pageable pageable) {
        UUID targetUserId = userId == null
            ? currentUserProvider.getCurrentUserId()
            : userId;
        Page<DiscussionRepost> repostPage = repostRepository.findByUserId(targetUserId, pageable);
        List<DiscussionRepost> reposts = repostPage.getContent();
        UUID viewerId = currentUserProvider.getCurrentUserId();

        if (reposts.isEmpty()) {
            return PageResponse.from(repostPage, repost ->
                toResponse(repost.getDiscussion(), false, viewerId, repost));
        }

        Set<UUID> discussionIds = reposts.stream()
            .map(repost -> repost.getDiscussion().getId())
            .collect(Collectors.toSet());
        Set<UUID> reacted = new HashSet<>(
            reactionRepository.findReactedDiscussionIds(viewerId, discussionIds));

        return PageResponse.from(repostPage, repost -> toResponse(
            repost.getDiscussion(),
            reacted.contains(repost.getDiscussion().getId()),
            viewerId,
            repost));
    }

    @Override
    @Transactional(readOnly = true)
    public DiscussionResponse getById(UUID discussionId) {
        ForumDiscussion discussion = loadDiscussion(discussionId);
        UUID viewerId = currentUserProvider.getCurrentUserId();
        boolean reacted = reactionRepository.findByDiscussionIdAndUserId(discussionId, viewerId).isPresent();

        log.info("Discussion viewed by user {}: {}", viewerId, discussion.getTitle());

        return toResponse(discussion, reacted, viewerId);
    }

    @Override
    @Transactional
    public DiscussionResponse update(UUID discussionId, DiscussionCreateRequest request) {
        ForumDiscussion discussion = loadOwned(discussionId);
        discussion.setTitle(request.title().strip());
        discussion.setContent(request.content());
        discussion.setCategory(request.category());
        if (request.anonymous() != null) {
            discussion.setIsAnonymous(request.anonymous());
        }
        if (request.language() != null) {
            discussion.setLanguage(request.language());
        }

        log.info("Discussion updated by user {}: {}", currentUserProvider.getCurrentUserId(), request.title());

        return getById(discussionRepository.save(discussion).getId());
    }

    @Override
    @Transactional
    public void delete(UUID discussionId) {
        ForumDiscussion discussion = loadOwned(discussionId);
        discussion.setDeletedAt(Instant.now());
        log.info("Discussion deleted by user {}: {}", currentUserProvider.getCurrentUserId(), discussion.getTitle());
        discussionRepository.save(discussion);
    }

    @Override
    @Transactional
    public int react(UUID discussionId) {
        ForumDiscussion discussion = loadDiscussionForUpdate(discussionId);
        UUID userId = currentUserProvider.getCurrentUserId();

        // forum_reactions has UNIQUE(discussion_id, user_id): a double tap must no-op
        // rather than 500, and the count must not drift.
        if (reactionRepository.findByDiscussionIdAndUserId(discussionId, userId).isPresent()) {
            return discussion.getReactionCount();
        }
        reactionRepository.save(ForumReaction.builder()
            .discussion(discussion)
            .user(loadCurrentUser())
            .reactionType(DEFAULT_REACTION)
            .build());
        discussionRepository.adjustReactionCount(discussionId, 1);

        log.info("Discussion reacted by user {}: {}", userId, discussion.getTitle());

        return discussion.getReactionCount() + 1;
    }

    @Override
    @Transactional
    public int unreact(UUID discussionId) {
        ForumDiscussion discussion = loadDiscussionForUpdate(discussionId);
        return reactionRepository
            .findByDiscussionIdAndUserId(discussionId, currentUserProvider.getCurrentUserId())
            .map(reaction -> {
                reactionRepository.delete(reaction);
                discussionRepository.adjustReactionCount(discussionId, -1);
                return discussion.getReactionCount() - 1;
            })
            .orElseGet(discussion::getReactionCount);
    }

    @Override
    @Transactional
    public void repost(UUID discussionId, RepostRequest request) {
        ForumDiscussion discussion = loadDiscussion(discussionId);
        UUID userId = currentUserProvider.getCurrentUserId();
        if (discussion.getUser() != null && userId.equals(discussion.getUser().getId())) {
            throw new ApiException("You cannot repost your own discussion", "CANNOT_REPOST_OWN_POST");
        }

        DiscussionRepost repost = repostRepository.findByDiscussionIdAndUserId(discussionId, userId)
            .orElse(null);
        String caption = normalizeCaption(request == null ? null : request.caption());
        if (repost == null) {
            repostRepository.save(DiscussionRepost.builder()
                .discussion(discussion)
                .user(loadCurrentUser())
                .caption(caption)
                .build());
        } else if (request != null && request.caption() != null
            && !java.util.Objects.equals(repost.getCaption(), caption)) {
            repost.setCaption(caption);
            repostRepository.save(repost);
        }
    }

    @Override
    @Transactional
    public void unrepost(UUID discussionId) {
        loadDiscussion(discussionId);
        repostRepository.deleteByDiscussionIdAndUserId(
            discussionId, currentUserProvider.getCurrentUserId());
    }

    @Override
    @Transactional
    public void report(UUID discussionId, DiscussionReportRequest request) {
        ForumDiscussion discussion = loadDiscussion(discussionId);
        UUID userId = currentUserProvider.getCurrentUserId();

        // One report per user, so report_count means "distinct people flagged this"
        // rather than "how many times someone tapped".
        if (reportRepository.existsByDiscussionIdAndUserId(discussionId, userId)) {
            return;
        }
        reportRepository.save(DiscussionReport.builder()
            .discussion(discussion)
            .user(loadCurrentUser())
            .reason(request.reason())
            .details(request.details())
            .build());
        discussionRepository.incrementReportCount(discussionId);

        // Worth a line: this is the only moderation signal in the product, and nothing
        // reviews the queue yet, so the log is the only place a report surfaces.
        log.info("Discussion reported: id={} reason={} by user={}", discussionId, request.reason(), userId);
    }

    @Override
    @Transactional
    public CommentResponse addComment(UUID discussionId, CommentCreateRequest request) {
        ForumDiscussion discussion = loadDiscussion(discussionId);
        User author = loadCurrentUser();
        ForumComment parent = resolveParent(request.parentCommentId(), discussionId);
        ForumComment comment = commentRepository.save(ForumComment.builder()
            .discussion(discussion)
            .user(author)
            .content(request.content())
            .parentComment(parent)
            .language(request.language())
            .likeCount(0)
            .build());
        discussionRepository.adjustCommentCount(discussionId, 1);
        // A freshly posted comment is not liked by its author yet.
        return toCommentResponse(comment, author.getId(), false);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> comments(UUID discussionId, Pageable pageable) {
        // Top-level only; replies come from the /replies route. Commenters are never
        // anonymous - only the discussion author can be - so replying to an anonymous
        // thread does not hide the replier.
        return hydrateComments(commentRepository.findTopLevel(discussionId, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> replies(UUID parentCommentId, Pageable pageable) {
        return hydrateComments(commentRepository.findReplies(parentCommentId, pageable));
    }

    @Override
    @Transactional
    public int likeComment(UUID commentId) {
        ForumComment comment = commentRepository.findByIdAndDeletedAtIsNullForUpdate(commentId)
            .orElseThrow(() -> new NotFoundException("Comment not found"));
        UUID userId = currentUserProvider.getCurrentUserId();

        // forum_comment_reactions has UNIQUE(comment_id, user_id): a double tap must no-op
        // rather than 500, and the count must not drift.
        if (commentReactionRepository.findByCommentIdAndUserId(commentId, userId).isPresent()) {
            return likeCount(comment);
        }
        commentReactionRepository.save(ForumCommentReaction.builder()
            .comment(comment)
            .user(loadCurrentUser())
            .reactionType(DEFAULT_REACTION)
            .build());
        commentRepository.adjustLikeCount(commentId, 1);

        log.info("Comment reacted : react count = {} ", likeCount(comment));

        return likeCount(comment) + 1;
    }

    @Override
    @Transactional
    public int unlikeComment(UUID commentId) {
        ForumComment comment = commentRepository.findByIdAndDeletedAtIsNullForUpdate(commentId)
            .orElseThrow(() -> new NotFoundException("Comment not found"));
        return commentReactionRepository
            .findByCommentIdAndUserId(commentId, currentUserProvider.getCurrentUserId())
            .map(reaction -> {
                commentReactionRepository.delete(reaction);
                commentRepository.adjustLikeCount(commentId, -1);
                log.info("Comment unliked : react count = {} ", likeCount(comment));
                return likeCount(comment) - 1;
            })
            .orElseGet(() -> likeCount(comment));

    }

    @Override
    @Transactional
    public CommentResponse updateComment(UUID commentId, String content) {
        UUID userId = currentUserProvider.getCurrentUserId();
        ForumComment comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
            .orElseThrow(() -> new NotFoundException("Comment not found"));
        if (comment.getUser() == null || !comment.getUser().getId().equals(userId)) {
            throw new NotFoundException("Comment not found");
        }
        comment.setContent(content);
        ForumComment saved = commentRepository.save(comment);
        boolean liked = commentReactionRepository.findByCommentIdAndUserId(commentId, userId).isPresent();
        return toCommentResponse(saved, userId, liked);
    }

    /** Resolves likedByMe for the whole page in one query rather than per row. */
    private PageResponse<CommentResponse> hydrateComments(Page<ForumComment> page) {
        UUID viewerId = currentUserProvider.getCurrentUserId();
        List<ForumComment> comments = page.getContent();
        if (comments.isEmpty()) {
            return PageResponse.from(page, comment -> toCommentResponse(comment, viewerId, false));
        }
        Set<UUID> ids = comments.stream().map(ForumComment::getId).collect(Collectors.toSet());
        Set<UUID> liked = new HashSet<>(commentReactionRepository.findReactedCommentIds(viewerId, ids));
        return PageResponse.from(page, comment -> toCommentResponse(comment, viewerId, liked.contains(comment.getId())));
    }

    private int likeCount(ForumComment comment) {
        return comment.getLikeCount() == null ? 0 : comment.getLikeCount();
    }

    /**
     * Keeps threading one level deep: replying to a reply attaches to the same top-level
     * parent, so the client never has to render arbitrary nesting. The parent must belong
     * to this discussion, otherwise a comment could be grafted onto an unrelated thread.
     */
    private ForumComment resolveParent(UUID parentCommentId, UUID discussionId) {
        if (parentCommentId == null) {
            return null;
        }
        ForumComment parent = commentRepository.findByIdAndDeletedAtIsNull(parentCommentId)
            .orElseThrow(() -> new NotFoundException("Parent comment not found"));
        if (parent.getDiscussion() == null || !parent.getDiscussion().getId().equals(discussionId)) {
            throw new NotFoundException("Parent comment not found");
        }
        return parent.getParentComment() == null ? parent : parent.getParentComment();
    }

    @Override
    @Transactional
    public void deleteComment(UUID commentId) {
        UUID userId = currentUserProvider.getCurrentUserId();
        ForumComment comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
            .orElseThrow(() -> new NotFoundException("Comment not found"));
        if (comment.getUser() == null || !comment.getUser().getId().equals(userId)) {
            throw new NotFoundException("Comment not found");
        }
        comment.setDeletedAt(Instant.now());
        commentRepository.save(comment);
        log.info("Comment deleted by user {}: {}", userId, comment.getContent());
        discussionRepository.adjustCommentCount(comment.getDiscussion().getId(), -1);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportedDiscussionResponse> reportedDiscussions(Pageable pageable) {
        return PageResponse.from(discussionRepository.findReportedForModeration(pageable), this::toReportedResponse);
    }

    @Override
    @Transactional
    public void removeAsAdmin(UUID discussionId) {
        ForumDiscussion discussion = loadDiscussion(discussionId);
        discussion.setDeletedAt(Instant.now());
        discussionRepository.save(discussion);
        emitDiscussionModerationNotification(discussion);
        log.info("Discussion {} removed by admin {}", discussionId, currentUserProvider.getCurrentUserId());
    }

    @Override
    @Transactional
    public void dismissReports(UUID discussionId) {
        loadDiscussion(discussionId);
        // Reviewed and fine: drop the report rows and zero the cached count so it leaves the
        // queue, keeping the discussion. A later report from someone new re-opens it.
        reportRepository.deleteByDiscussionId(discussionId);
        discussionRepository.clearReportCount(discussionId);
        log.info("Reports dismissed on discussion {} by admin {}", discussionId, currentUserProvider.getCurrentUserId());
    }

    private void emitDiscussionModerationNotification(ForumDiscussion discussion) {
        User author = discussion.getUser();
        if (author == null || author.getId() == null || discussion.getId() == null
            || discussion.getTitle() == null || discussion.getTitle().isBlank()) {
            log.warn("Skipping discussion moderation notification because discussion details are incomplete: id={}",
                discussion.getId());
            return;
        }

        notificationEmitter.emit(new NotificationRequest(
            author.getId(),
            DISCUSSION_MODERATION_NOTIFICATION_TEMPLATE,
            DISCUSSION_MODERATION_NOTIFICATION_TYPE,
            Map.of(
                "discussionTitle", discussion.getTitle(),
                "moderationAction", DISCUSSION_MODERATION_ACTION_REMOVED),
            "DISCUSSION",
            discussion.getId(),
            "forum-discussion-moderated:" + discussion.getId() + ":" + author.getId()));
    }

    /**
     * The admin queue deliberately reveals the author even for anonymous discussions: the
     * whole reason the author is stored is moderation accountability. postedAnonymously
     * flags that it was anonymous to the public, without hiding who wrote it from the admin.
     */
    private ReportedDiscussionResponse toReportedResponse(ForumDiscussion discussion) {
        return new ReportedDiscussionResponse(
            discussion.getId(),
            summary(discussion.getUser()),
            Boolean.TRUE.equals(discussion.getIsAnonymous()),
            discussion.getTitle(),
            discussion.getContent(),
            discussion.getCategory(),
            discussion.getReportCount() == null ? 0 : discussion.getReportCount(),
            discussion.getCreatedAt());
    }

    /** Resolves reactedByMe for the whole page in one query rather than per row. */
    private PageResponse<DiscussionResponse> hydrate(Page<ForumDiscussion> page) {
        UUID viewerId = currentUserProvider.getCurrentUserId();
        List<ForumDiscussion> discussions = page.getContent();
        if (discussions.isEmpty()) {
            return PageResponse.from(page, d -> toResponse(d, false, viewerId));
        }
        Set<UUID> ids = discussions.stream().map(ForumDiscussion::getId).collect(Collectors.toSet());
        Set<UUID> reacted = new HashSet<>(reactionRepository.findReactedDiscussionIds(viewerId, ids));
        return PageResponse.from(page, d -> toResponse(d, reacted.contains(d.getId()), viewerId));
    }

    private ForumDiscussion loadDiscussion(UUID discussionId) {
        return discussionRepository.findByIdAndDeletedAtIsNull(discussionId)
            .orElseThrow(() -> new NotFoundException("Discussion not found"));
    }

    private ForumDiscussion loadDiscussionForUpdate(UUID discussionId) {
        return discussionRepository.findByIdAndDeletedAtIsNullForUpdate(discussionId)
            .orElseThrow(() -> new NotFoundException("Discussion not found"));
    }

    private ForumDiscussion loadOwned(UUID discussionId) {
        return discussionRepository
            .findByIdAndUserIdAndDeletedAtIsNull(discussionId, currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("Discussion not found"));
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private String normalizeCaption(String caption) {
        if (caption == null) {
            return null;
        }
        String normalized = caption.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * The only place anonymity is enforced. The entity always carries the author; this
     * withholds it. Get this wrong and an anonymous discussion is not anonymous.
     */
    private DiscussionResponse toResponse(ForumDiscussion discussion, boolean reactedByMe, UUID viewerId) {
        return toResponse(discussion, reactedByMe, viewerId, null);
    }

    private DiscussionResponse toResponse(ForumDiscussion discussion, boolean reactedByMe,
                                          UUID viewerId, DiscussionRepost repost) {
        User author = discussion.getUser();
        boolean anonymous = Boolean.TRUE.equals(discussion.getIsAnonymous());
        boolean mine = author != null && author.getId().equals(viewerId);
        User reposter = repost == null ? null : repost.getUser();

        return new DiscussionResponse(
            discussion.getId(),
            anonymous ? null : summary(author),
            discussion.getTitle(),
            discussion.getContent(),
            discussion.getCategory(),
            anonymous,
            discussion.getLanguage(),
            discussion.getReactionCount(),
            discussion.getCommentCount(),
            reactedByMe,
            mine,
            discussion.getCreatedAt(),
            repost != null,
            displayName(reposter),
            repost == null ? null : repost.getCaption(),
            reposter == null ? null : reposter.getId(),
            repost == null ? null : discussion.getId()
        );
    }

    private CommentResponse toCommentResponse(ForumComment comment, UUID viewerId, boolean likedByMe) {
        User author = comment.getUser();
        return new CommentResponse(
            comment.getId(),
            summary(author),
            comment.getContent(),
            comment.getParentComment() == null ? null : comment.getParentComment().getId(),
            comment.getLanguage(),
            likeCount(comment),
            likedByMe,
            author != null && author.getId().equals(viewerId),
            comment.getCreatedAt()
        );
    }

    private PostResponse.AuthorSummary summary(User user) {
        return user == null ? null : new PostResponse.AuthorSummary(
            user.getId(), user.getFullName(), user.getUsername(), user.getAvatarUrl());
    }

    private String displayName(User user) {
        if (user == null) {
            return null;
        }
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        return user.getUsername();
    }
}
