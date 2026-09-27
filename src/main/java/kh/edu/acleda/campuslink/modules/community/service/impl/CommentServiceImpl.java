package kh.edu.acleda.campuslink.modules.community.service.impl;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kh.edu.acleda.campuslink.modules.community.dto.request.CommentCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.CommentResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse;
import kh.edu.acleda.campuslink.modules.community.entity.Post;
import kh.edu.acleda.campuslink.modules.community.entity.PostComment;
import kh.edu.acleda.campuslink.modules.community.entity.PostCommentReaction;
import kh.edu.acleda.campuslink.modules.community.repository.PostCommentReactionRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PostCommentRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PostRepository;
import kh.edu.acleda.campuslink.modules.community.service.CommentService;
import kh.edu.acleda.campuslink.modules.community.service.PostVisibilityService;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class CommentServiceImpl implements CommentService {

    private static final String DEFAULT_REACTION = "LIKE";

    private final PostCommentRepository commentRepository;
    private final PostCommentReactionRepository commentReactionRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final NotificationEmitter notificationEmitter;
    private final PostVisibilityService postVisibilityService;

    @Autowired
    public CommentServiceImpl(PostCommentRepository commentRepository,
                              PostCommentReactionRepository commentReactionRepository,
                              PostRepository postRepository,
                              UserRepository userRepository,
                              CurrentUserProvider currentUserProvider,
                              NotificationEmitter notificationEmitter,
                              PostVisibilityService postVisibilityService) {
        this.commentRepository = commentRepository;
        this.commentReactionRepository = commentReactionRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.notificationEmitter = notificationEmitter;
        this.postVisibilityService = postVisibilityService;
    }

    public CommentServiceImpl(PostCommentRepository commentRepository,
                              PostCommentReactionRepository commentReactionRepository,
                              PostRepository postRepository,
                              UserRepository userRepository,
                              CurrentUserProvider currentUserProvider,
                              NotificationEmitter notificationEmitter) {
        this(commentRepository, commentReactionRepository, postRepository, userRepository,
            currentUserProvider, notificationEmitter, null);
    }

    public CommentServiceImpl(PostCommentRepository commentRepository,
                              PostCommentReactionRepository commentReactionRepository,
                              PostRepository postRepository,
                              UserRepository userRepository,
                              CurrentUserProvider currentUserProvider) {
        this(commentRepository, commentReactionRepository, postRepository, userRepository,
            currentUserProvider, null, null);
    }

    @Override
    @Transactional
    public CommentResponse add(UUID postId, CommentCreateRequest request) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
            .orElseThrow(() -> new NotFoundException("Post not found"));
        postVisibilityService.requireCanView(post, currentUserProvider.getCurrentUserId());
        User author = loadCurrentUser();

        PostComment parent = resolveParent(request.parentCommentId(), postId);

        PostComment comment = PostComment.builder()
            .post(post)
            .user(author)
            .content(request.content())
            .parentComment(parent)
            .language(request.language())
            .likeCount(0)
            .build();

        PostComment saved = commentRepository.save(comment);
        postRepository.adjustCommentCount(postId, 1);

        log.info("Comment added: id={} by user={}", saved.getId(), author.getId());

        emitCommentNotifications(post, saved, author, parent);

        // A freshly posted comment is not liked by its author yet.
        return toResponse(saved, author.getId(), false);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> list(UUID postId, Pageable pageable) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
            .orElseThrow(() -> new NotFoundException("Post not found"));
        postVisibilityService.requireCanView(post, currentUserProvider.getCurrentUserId());
        return hydrate(commentRepository.findTopLevel(postId, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> replies(UUID parentCommentId, Pageable pageable) {
        PostComment parent = commentRepository.findByIdAndDeletedAtIsNull(parentCommentId)
            .orElseThrow(() -> new NotFoundException("Comment not found"));
        postVisibilityService.requireCanView(parent.getPost(), currentUserProvider.getCurrentUserId());
        return hydrate(commentRepository.findReplies(parentCommentId, pageable));
    }

    @Override
    @Transactional
    public int like(UUID commentId) {
        PostComment comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
            .orElseThrow(() -> new NotFoundException("Comment not found"));
        UUID userId = currentUserProvider.getCurrentUserId();
        postVisibilityService.requireCanView(comment.getPost(), userId);

        // post_comment_reactions has UNIQUE(comment_id, user_id): a double tap must no-op
        // rather than 500, and the count must not drift.
        if (commentReactionRepository.findByCommentIdAndUserId(commentId, userId).isPresent()) {
            return likeCount(comment);
        }
        commentReactionRepository.save(PostCommentReaction.builder()
            .comment(comment)
            .user(loadCurrentUser())
            .reactionType(DEFAULT_REACTION)
            .build());
        commentRepository.adjustLikeCount(commentId, 1);
        return likeCount(comment) + 1;
    }

    @Override
    @Transactional
    public int unlike(UUID commentId) {
        PostComment comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
            .orElseThrow(() -> new NotFoundException("Comment not found"));
        postVisibilityService.requireCanView(comment.getPost(), currentUserProvider.getCurrentUserId());
        return commentReactionRepository
            .findByCommentIdAndUserId(commentId, currentUserProvider.getCurrentUserId())
            .map(reaction -> {
                commentReactionRepository.delete(reaction);
                commentRepository.adjustLikeCount(commentId, -1);
                return likeCount(comment) - 1;
            })
            .orElseGet(() -> likeCount(comment));
    }

    /** Resolves likedByMe for the whole page in one query rather than per row. */
    private PageResponse<CommentResponse> hydrate(Page<PostComment> page) {
        UUID viewerId = currentUserProvider.getCurrentUserId();
        List<PostComment> comments = page.getContent();
        if (comments.isEmpty()) {
            return PageResponse.from(page, comment -> toResponse(comment, viewerId, false));
        }
        Set<UUID> ids = comments.stream().map(PostComment::getId).collect(Collectors.toSet());
        Set<UUID> liked = new HashSet<>(commentReactionRepository.findReactedCommentIds(viewerId, ids));
        return PageResponse.from(page, comment -> toResponse(comment, viewerId, liked.contains(comment.getId())));
    }

    private int likeCount(PostComment comment) {
        return comment.getLikeCount() == null ? 0 : comment.getLikeCount();
    }

    @Override
    @Transactional
    public void delete(UUID commentId) {
        UUID userId = currentUserProvider.getCurrentUserId();
        PostComment comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
            .orElseThrow(() -> new NotFoundException("Comment not found"));
        postVisibilityService.requireCanView(comment.getPost(), userId);

        // Owner-scoped, and phrased as not found so it does not reveal the row exists.
        if (comment.getUser() == null || !comment.getUser().getId().equals(userId)) {
            throw new NotFoundException("Comment not found");
        }

        comment.setDeletedAt(Instant.now());
        commentRepository.save(comment);
        log.info("Comment deleted: id={} by user={}", commentId, userId);
        postRepository.adjustCommentCount(comment.getPost().getId(), -1);
    }

    /**
     * Keeps threading one level deep: replying to a reply attaches to the same
     * top-level parent, so the client never has to render arbitrary nesting.
     * The parent must belong to this post, otherwise a comment could be grafted onto
     * an unrelated thread.
     */
    private PostComment resolveParent(UUID parentCommentId, UUID postId) {
        if (parentCommentId == null) {
            return null;
        }
        PostComment parent = commentRepository.findByIdAndDeletedAtIsNull(parentCommentId)
            .orElseThrow(() -> new NotFoundException("Parent comment not found"));
        if (!parent.getPost().getId().equals(postId)) {
            throw new NotFoundException("Parent comment not found");
        }
        return parent.getParentComment() == null ? parent : parent.getParentComment();
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private CommentResponse toResponse(PostComment comment, UUID viewerId, boolean likedByMe) {
        User author = comment.getUser();
        return new CommentResponse(
            comment.getId(),
            author == null ? null : new PostResponse.AuthorSummary(
                author.getId(), author.getFullName(), author.getUsername(), author.getAvatarUrl()),
            comment.getContent(),
            comment.getParentComment() == null ? null : comment.getParentComment().getId(),
            comment.getLanguage(),
            likeCount(comment),
            likedByMe,
            author != null && author.getId().equals(viewerId),
            comment.getCreatedAt()
        );
    }

    private void emitCommentNotifications(Post post, PostComment comment, User author, PostComment parent) {
        if (notificationEmitter == null || post == null || comment == null || author == null) {
            return;
        }

        String authorName = resolveAuthorName(author);
        String snippet = truncateSnippet(comment.getContent());

        try {
            // If replying to an existing comment, notify the parent comment's author
            if (parent != null && parent.getUser() != null
                && !parent.getUser().getId().equals(author.getId())) {
                notificationEmitter.emit(new NotificationRequest(
                    parent.getUser().getId(),
                    "COMMUNITY_COMMENT_REPLY",
                    "COMMUNITY",
                    Map.of(
                        "replierName", authorName,
                        "replySnippet", snippet,
                        "postId", post.getId().toString(),
                        "commentId", comment.getId().toString(),
                        "parentCommentId", parent.getId().toString()
                    ),
                    "POST",
                    post.getId(),
                    "comment-reply:" + comment.getId() + ":" + parent.getUser().getId(),
                    Set.of(NotificationChannel.IN_APP, NotificationChannel.MOBILE_PUSH)
                ));
            }

            // Notify post author (unless author is commenter or parent author is already post author)
            User postAuthor = post.getUser();
            if (postAuthor != null && !postAuthor.getId().equals(author.getId())) {
                boolean parentAuthorIsPostAuthor = parent != null && parent.getUser() != null
                    && parent.getUser().getId().equals(postAuthor.getId());
                if (!parentAuthorIsPostAuthor) {
                    notificationEmitter.emit(new NotificationRequest(
                        postAuthor.getId(),
                        "COMMUNITY_POST_COMMENT",
                        "COMMUNITY",
                        Map.of(
                            "commenterName", authorName,
                            "commentSnippet", snippet,
                            "postId", post.getId().toString(),
                            "commentId", comment.getId().toString()
                        ),
                        "POST",
                        post.getId(),
                        "post-comment:" + comment.getId() + ":" + postAuthor.getId(),
                        Set.of(NotificationChannel.IN_APP, NotificationChannel.MOBILE_PUSH)
                    ));
                }
            }
        } catch (RuntimeException exception) {
            log.warn("Failed to emit comment notification for comment {}: {}", comment.getId(), exception.getMessage());
        }
    }

    private String resolveAuthorName(User user) {
        if (user == null) {
            return "Someone";
        }
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName().strip();
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername().strip();
        }
        return "Someone";
    }

    private String truncateSnippet(String content) {
        if (content == null || content.isBlank()) {
            return "...";
        }
        String stripped = content.strip();
        if (stripped.length() <= 60) {
            return stripped;
        }
        return stripped.substring(0, 57) + "...";
    }
}
