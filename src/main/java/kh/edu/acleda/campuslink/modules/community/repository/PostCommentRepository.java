package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.PostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PostCommentRepository extends JpaRepository<PostComment, UUID> {

    /**
     * Top-level comments only; replies hang off parentCommentId and are fetched with
     * their parent. countQuery is explicit because JOIN FETCH cannot be counted.
     */
    @Query(value = "SELECT c FROM PostComment c JOIN FETCH c.user "
        + "WHERE c.post.id = :postId AND c.deletedAt IS NULL AND c.parentComment IS NULL",
        countQuery = "SELECT COUNT(c) FROM PostComment c "
            + "WHERE c.post.id = :postId AND c.deletedAt IS NULL AND c.parentComment IS NULL")
    Page<PostComment> findTopLevel(@Param("postId") UUID postId, Pageable pageable);

    @Query(value = "SELECT c FROM PostComment c JOIN FETCH c.user "
        + "WHERE c.parentComment.id = :parentId AND c.deletedAt IS NULL",
        countQuery = "SELECT COUNT(c) FROM PostComment c WHERE c.parentComment.id = :parentId AND c.deletedAt IS NULL")
    Page<PostComment> findReplies(@Param("parentId") UUID parentId, Pageable pageable);

    Optional<PostComment> findByIdAndDeletedAtIsNull(UUID id);
}
