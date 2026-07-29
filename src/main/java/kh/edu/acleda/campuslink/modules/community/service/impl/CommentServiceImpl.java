package kh.edu.acleda.campuslink.modules.community.service.impl;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
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
public class CommentServiceImpl implements CommentService {

    private static final String DEFAULT_REACTION = "LIKE";

    private final PostCommentRepository commentRepository;
    private final PostCommentReactionRepository commentReactionRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public CommentServiceImpl(PostCommentRepository commentRepository,
                              PostCommentReactionRepository commentReactionRepository,
                              PostRepository postRepository,
                              UserRepository userRepository,
                              CurrentUserProvider currentUserProvider) {
        this.commentRepository = commentRepository;
        this.commentReactionRepository = commentReactionRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public CommentResponse add(UUID postId, CommentCreateRequest request) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
            .orElseThrow(() -> new NotFoundException("Post not found"));
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
        // A freshly posted comment is not liked by its author yet.
        return toResponse(saved, author.getId(), false);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> list(UUID postId, Pageable pageable) {
        return hydrate(commentRepository.findTopLevel(postId, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> replies(UUID parentCommentId, Pageable pageable) {
        return hydrate(commentRepository.findReplies(parentCommentId, pageable));
    }

    @Override
    @Transactional
    public int like(UUID commentId) {
        PostComment comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
            .orElseThrow(() -> new NotFoundException("Comment not found"));
        UUID userId = currentUserProvider.getCurrentUserId();

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

        // Owner-scoped, and phrased as not found so it does not reveal the row exists.
        if (comment.getUser() == null || !comment.getUser().getId().equals(userId)) {
            throw new NotFoundException("Comment not found");
        }

        comment.setDeletedAt(Instant.now());
        commentRepository.save(comment);
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
}
