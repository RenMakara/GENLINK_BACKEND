package kh.edu.acleda.campuslink.modules.community.service.impl;

import java.time.Instant;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.CommentCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.CommentResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse;
import kh.edu.acleda.campuslink.modules.community.entity.Post;
import kh.edu.acleda.campuslink.modules.community.entity.PostComment;
import kh.edu.acleda.campuslink.modules.community.repository.PostCommentRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PostRepository;
import kh.edu.acleda.campuslink.modules.community.service.CommentService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentServiceImpl implements CommentService {

    private final PostCommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public CommentServiceImpl(PostCommentRepository commentRepository,
                              PostRepository postRepository,
                              UserRepository userRepository,
                              CurrentUserProvider currentUserProvider) {
        this.commentRepository = commentRepository;
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
        return toResponse(saved, author.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> list(UUID postId, Pageable pageable) {
        UUID viewerId = currentUserProvider.getCurrentUserId();
        return PageResponse.from(commentRepository.findTopLevel(postId, pageable),
            comment -> toResponse(comment, viewerId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> replies(UUID parentCommentId, Pageable pageable) {
        UUID viewerId = currentUserProvider.getCurrentUserId();
        return PageResponse.from(commentRepository.findReplies(parentCommentId, pageable),
            comment -> toResponse(comment, viewerId));
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

    private CommentResponse toResponse(PostComment comment, UUID viewerId) {
        User author = comment.getUser();
        return new CommentResponse(
            comment.getId(),
            author == null ? null : new PostResponse.AuthorSummary(
                author.getId(), author.getFullName(), author.getUsername(), author.getAvatarUrl()),
            comment.getContent(),
            comment.getParentComment() == null ? null : comment.getParentComment().getId(),
            comment.getLanguage(),
            comment.getLikeCount(),
            author != null && author.getId().equals(viewerId),
            comment.getCreatedAt()
        );
    }
}
