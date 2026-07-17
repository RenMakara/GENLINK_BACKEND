package kh.edu.acleda.campuslink.modules.community.service.impl;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kh.edu.acleda.campuslink.modules.community.dto.request.CommentCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.DiscussionCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.DiscussionReportRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.CommentResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.DiscussionResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse;
import kh.edu.acleda.campuslink.modules.community.entity.DiscussionReport;
import kh.edu.acleda.campuslink.modules.community.entity.ForumComment;
import kh.edu.acleda.campuslink.modules.community.entity.ForumDiscussion;
import kh.edu.acleda.campuslink.modules.community.entity.ForumReaction;
import kh.edu.acleda.campuslink.modules.community.repository.DiscussionReportRepository;
import kh.edu.acleda.campuslink.modules.community.repository.ForumCommentRepository;
import kh.edu.acleda.campuslink.modules.community.repository.ForumDiscussionRepository;
import kh.edu.acleda.campuslink.modules.community.repository.ForumReactionRepository;
import kh.edu.acleda.campuslink.modules.community.service.DiscussionService;
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

@Slf4j
@Service
public class DiscussionServiceImpl implements DiscussionService {

    private static final String DEFAULT_REACTION = "LIKE";

    private final ForumDiscussionRepository discussionRepository;
    private final ForumCommentRepository commentRepository;
    private final ForumReactionRepository reactionRepository;
    private final DiscussionReportRepository reportRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public DiscussionServiceImpl(ForumDiscussionRepository discussionRepository,
                                 ForumCommentRepository commentRepository,
                                 ForumReactionRepository reactionRepository,
                                 DiscussionReportRepository reportRepository,
                                 UserRepository userRepository,
                                 CurrentUserProvider currentUserProvider) {
        this.discussionRepository = discussionRepository;
        this.commentRepository = commentRepository;
        this.reactionRepository = reactionRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
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
    public DiscussionResponse getById(UUID discussionId) {
        ForumDiscussion discussion = loadDiscussion(discussionId);
        UUID viewerId = currentUserProvider.getCurrentUserId();
        boolean reacted = reactionRepository.findByDiscussionIdAndUserId(discussionId, viewerId).isPresent();
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
        return getById(discussionRepository.save(discussion).getId());
    }

    @Override
    @Transactional
    public void delete(UUID discussionId) {
        ForumDiscussion discussion = loadOwned(discussionId);
        discussion.setDeletedAt(Instant.now());
        discussionRepository.save(discussion);
    }

    @Override
    @Transactional
    public int react(UUID discussionId) {
        ForumDiscussion discussion = loadDiscussion(discussionId);
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
        return discussion.getReactionCount() + 1;
    }

    @Override
    @Transactional
    public int unreact(UUID discussionId) {
        ForumDiscussion discussion = loadDiscussion(discussionId);
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
        ForumComment comment = commentRepository.save(ForumComment.builder()
            .discussion(discussion)
            .user(author)
            .content(request.content())
            .language(request.language())
            .build());
        discussionRepository.adjustCommentCount(discussionId, 1);
        return toCommentResponse(comment, author.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> comments(UUID discussionId, Pageable pageable) {
        UUID viewerId = currentUserProvider.getCurrentUserId();
        // Commenters are never anonymous - only the discussion author can be. Replying
        // to an anonymous thread does not hide the replier.
        return PageResponse.from(commentRepository.findByDiscussion(discussionId, pageable),
            comment -> toCommentResponse(comment, viewerId));
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
        discussionRepository.adjustCommentCount(comment.getDiscussion().getId(), -1);
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

    private ForumDiscussion loadOwned(UUID discussionId) {
        return discussionRepository
            .findByIdAndUserIdAndDeletedAtIsNull(discussionId, currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("Discussion not found"));
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }

    /**
     * The only place anonymity is enforced. The entity always carries the author; this
     * withholds it. Get this wrong and an anonymous discussion is not anonymous.
     */
    private DiscussionResponse toResponse(ForumDiscussion discussion, boolean reactedByMe, UUID viewerId) {
        User author = discussion.getUser();
        boolean anonymous = Boolean.TRUE.equals(discussion.getIsAnonymous());
        boolean mine = author != null && author.getId().equals(viewerId);

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
            discussion.getCreatedAt()
        );
    }

    private CommentResponse toCommentResponse(ForumComment comment, UUID viewerId) {
        User author = comment.getUser();
        return new CommentResponse(
            comment.getId(),
            summary(author),
            comment.getContent(),
            null,
            comment.getLanguage(),
            0,
            author != null && author.getId().equals(viewerId),
            comment.getCreatedAt()
        );
    }

    private PostResponse.AuthorSummary summary(User user) {
        return user == null ? null : new PostResponse.AuthorSummary(
            user.getId(), user.getFullName(), user.getUsername(), user.getAvatarUrl());
    }
}
