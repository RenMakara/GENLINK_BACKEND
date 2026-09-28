package kh.edu.acleda.campuslink.modules.community;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.CommentCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.CommentResponse;
import kh.edu.acleda.campuslink.modules.community.entity.Post;
import kh.edu.acleda.campuslink.modules.community.entity.PostComment;
import kh.edu.acleda.campuslink.modules.community.entity.PostCommentReaction;
import kh.edu.acleda.campuslink.modules.community.repository.PostCommentReactionRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PostCommentRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PostRepository;
import kh.edu.acleda.campuslink.modules.community.service.PostVisibilityService;
import kh.edu.acleda.campuslink.modules.community.service.impl.CommentServiceImpl;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommentServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID POST_ID = UUID.randomUUID();

    @Mock private PostCommentRepository commentRepository;
    @Mock private PostCommentReactionRepository commentReactionRepository;
    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter notificationEmitter;
    @Mock private PostVisibilityService postVisibilityService;

    private CommentServiceImpl service;
    private User author;
    private Post post;

    @BeforeEach
    void setUp() {
        service = new CommentServiceImpl(commentRepository, commentReactionRepository,
            postRepository, userRepository, currentUserProvider, notificationEmitter, postVisibilityService);

        author = new User();
        author.setFullName("Author");
        author.setUsername("author");
        ReflectionTestUtils.setField(author, "id", USER_ID);

        post = Post.builder().likeCount(0).commentCount(0).build();
        ReflectionTestUtils.setField(post, "id", POST_ID);

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(author));
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(PostComment.class))).thenAnswer(i -> {
            PostComment c = i.getArgument(0);
            if (c.getId() == null) {
                ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
            }
            return c;
        });
        when(commentReactionRepository.findReactedCommentIds(any(), anyCollection())).thenReturn(List.of());
    }

    @Test
    void addingATopLevelCommentPersistsItAndBumpsTheCount() {
        service.add(POST_ID, new CommentCreateRequest("Nice post", null, Language.ENGLISH));

        var captor = org.mockito.ArgumentCaptor.forClass(PostComment.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getParentComment()).isNull();
        verify(postRepository).adjustCommentCount(POST_ID, 1);
    }

    @Test
    void restrictedPostBlocksCommentCreationAndListing() {
        doThrow(new NotFoundException("Post not found"))
            .when(postVisibilityService).requireCanView(post, USER_ID);

        assertThatThrownBy(() -> service.add(POST_ID,
            new CommentCreateRequest("hidden comment", null, Language.ENGLISH)))
            .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.list(POST_ID, PageRequest.of(0, 20)))
            .isInstanceOf(NotFoundException.class);

        verify(commentRepository, never()).save(any(PostComment.class));
        verify(commentRepository, never()).findTopLevel(eq(POST_ID), any());
        verify(postRepository, never()).adjustCommentCount(POST_ID, 1);
    }

    @Test
    void restrictedPostBlocksReplyReadsAndReactions() {
        UUID commentId = UUID.randomUUID();
        PostComment comment = PostComment.builder().post(post).user(author).content("hidden")
            .likeCount(0).build();
        ReflectionTestUtils.setField(comment, "id", commentId);
        when(commentRepository.findByIdAndDeletedAtIsNull(commentId)).thenReturn(Optional.of(comment));
        doThrow(new NotFoundException("Post not found"))
            .when(postVisibilityService).requireCanView(post, USER_ID);

        assertThatThrownBy(() -> service.replies(commentId, PageRequest.of(0, 20)))
            .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.like(commentId)).isInstanceOf(NotFoundException.class);

        verify(commentRepository, never()).findReplies(eq(commentId), any());
        verify(commentReactionRepository, never()).save(any(PostCommentReaction.class));
    }

    /** Threading is one level deep: a reply to a reply attaches to the top-level parent. */
    @Test
    void replyingToAReplyCollapsesToTheTopLevelParent() {
        UUID topId = UUID.randomUUID();
        UUID replyId = UUID.randomUUID();
        PostComment top = PostComment.builder().post(post).user(author).content("top").likeCount(0).build();
        ReflectionTestUtils.setField(top, "id", topId);
        PostComment reply = PostComment.builder().post(post).user(author).content("reply")
            .parentComment(top).likeCount(0).build();
        ReflectionTestUtils.setField(reply, "id", replyId);
        when(commentRepository.findByIdAndDeletedAtIsNull(replyId)).thenReturn(Optional.of(reply));

        service.add(POST_ID, new CommentCreateRequest("nested", replyId, Language.ENGLISH));

        var captor = org.mockito.ArgumentCaptor.forClass(PostComment.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getParentComment()).isEqualTo(top);
    }

    @Test
    void replyingToACommentFromAnotherPostIsNotFound() {
        UUID parentId = UUID.randomUUID();
        Post otherPost = Post.builder().likeCount(0).commentCount(0).build();
        ReflectionTestUtils.setField(otherPost, "id", UUID.randomUUID());
        PostComment parent = PostComment.builder().post(otherPost).user(author).content("q").likeCount(0).build();
        ReflectionTestUtils.setField(parent, "id", parentId);
        when(commentRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.add(POST_ID, new CommentCreateRequest("x", parentId, Language.ENGLISH)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void likingACommentIsIdempotent() {
        PostComment comment = likableComment(2);
        UUID commentId = comment.getId();
        when(commentReactionRepository.findByCommentIdAndUserId(commentId, USER_ID))
            .thenReturn(Optional.of(PostCommentReaction.builder().build()));

        assertThat(service.like(commentId)).isEqualTo(2);
        verify(commentRepository).findByIdAndDeletedAtIsNullForUpdate(commentId);
        verify(commentReactionRepository, never()).save(any(PostCommentReaction.class));
        verify(commentRepository, never()).adjustLikeCount(any(), anyInt());
    }

    @Test
    void likingACommentCreatesTheReactionAndIncrementsTheCount() {
        PostComment comment = likableComment(2);
        UUID commentId = comment.getId();
        when(commentReactionRepository.findByCommentIdAndUserId(commentId, USER_ID)).thenReturn(Optional.empty());

        assertThat(service.like(commentId)).isEqualTo(3);
        verify(commentRepository).findByIdAndDeletedAtIsNullForUpdate(commentId);
        verify(commentRepository).adjustLikeCount(commentId, 1);
    }

    @Test
    void unlikingACommentNeverLikedIsANoOp() {
        PostComment comment = likableComment(2);
        UUID commentId = comment.getId();
        when(commentReactionRepository.findByCommentIdAndUserId(commentId, USER_ID)).thenReturn(Optional.empty());

        assertThat(service.unlike(commentId)).isEqualTo(2);
        verify(commentRepository, never()).adjustLikeCount(any(), anyInt());
    }

    @Test
    void theCommentListResolvesLikedByMeAndTheCount() {
        PostComment top = PostComment.builder().post(post).user(author).content("top").likeCount(5).build();
        UUID cid = UUID.randomUUID();
        ReflectionTestUtils.setField(top, "id", cid);
        when(commentRepository.findTopLevel(eq(POST_ID), any())).thenReturn(new PageImpl<>(List.of(top)));
        when(commentReactionRepository.findReactedCommentIds(eq(USER_ID), anyCollection())).thenReturn(List.of(cid));

        CommentResponse row = service.list(POST_ID, PageRequest.of(0, 20)).content().get(0);

        assertThat(row.likeCount()).isEqualTo(5);
        assertThat(row.likedByMe()).isTrue();
    }

    private PostComment likableComment(int likeCount) {
        PostComment comment = PostComment.builder().post(post).user(author).content("c").likeCount(likeCount).build();
        ReflectionTestUtils.setField(comment, "id", UUID.randomUUID());
        when(commentRepository.findByIdAndDeletedAtIsNull(comment.getId())).thenReturn(Optional.of(comment));
        when(commentRepository.findByIdAndDeletedAtIsNullForUpdate(comment.getId()))
            .thenReturn(Optional.of(comment));
        return comment;
    }

    @Test
    void addingCommentEmitsPostCommentNotificationToPostAuthor() {
        UUID postAuthorId = UUID.randomUUID();
        User postAuthor = new User();
        postAuthor.setFullName("Post Author");
        ReflectionTestUtils.setField(postAuthor, "id", postAuthorId);
        post.setUser(postAuthor);

        service.add(POST_ID, new CommentCreateRequest("Great insight!", null, Language.ENGLISH));

        org.mockito.ArgumentCaptor<kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest> captor =
            org.mockito.ArgumentCaptor.forClass(kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest.class);
        verify(notificationEmitter).emit(captor.capture());
        kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest req = captor.getValue();
        assertThat(req.recipientUserId()).isEqualTo(postAuthorId);
        assertThat(req.templateKey()).isEqualTo("COMMUNITY_POST_COMMENT");
        assertThat(req.referenceType()).isEqualTo("POST");
        assertThat(req.referenceId()).isEqualTo(POST_ID);
        assertThat(req.channels()).containsExactlyInAnyOrder(
            kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel.IN_APP,
            kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel.MOBILE_PUSH);
    }

    @Test
    void replyingToCommentEmitsCommentReplyNotification() {
        UUID postAuthorId = UUID.randomUUID();
        User postAuthor = new User();
        ReflectionTestUtils.setField(postAuthor, "id", postAuthorId);
        post.setUser(postAuthor);

        UUID parentAuthorId = UUID.randomUUID();
        User parentAuthor = new User();
        parentAuthor.setFullName("Parent Author");
        ReflectionTestUtils.setField(parentAuthor, "id", parentAuthorId);

        UUID parentId = UUID.randomUUID();
        PostComment parent = PostComment.builder().post(post).user(parentAuthor).content("Original").likeCount(0).build();
        ReflectionTestUtils.setField(parent, "id", parentId);
        when(commentRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parent));

        service.add(POST_ID, new CommentCreateRequest("I agree!", parentId, Language.ENGLISH));

        org.mockito.ArgumentCaptor<kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest> captor =
            org.mockito.ArgumentCaptor.forClass(kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest.class);
        verify(notificationEmitter, org.mockito.Mockito.atLeastOnce()).emit(captor.capture());
        List<kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest> emitted = captor.getAllValues();
        assertThat(emitted).anyMatch(r -> r.recipientUserId().equals(parentAuthorId)
            && "COMMUNITY_COMMENT_REPLY".equals(r.templateKey()));
    }

    @Test
    void commentingOnOwnPostDoesNotEmitNotification() {
        post.setUser(author);

        service.add(POST_ID, new CommentCreateRequest("Self comment", null, Language.ENGLISH));

        verify(notificationEmitter, never()).emit(any());
    }
}
