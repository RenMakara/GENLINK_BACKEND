package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.ForumComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;

public interface ForumCommentRepository extends JpaRepository<ForumComment, UUID> {

    /**
     * Top-level comments only; replies hang off parentCommentId and come from findReplies.
     * countQuery is explicit because JOIN FETCH cannot be counted.
     */
    @Query(value = "SELECT c FROM ForumComment c LEFT JOIN FETCH c.user "
        + "WHERE c.discussion.id = :discussionId AND c.deletedAt IS NULL AND c.parentComment IS NULL",
        countQuery = "SELECT COUNT(c) FROM ForumComment c "
            + "WHERE c.discussion.id = :discussionId AND c.deletedAt IS NULL AND c.parentComment IS NULL")
    Page<ForumComment> findTopLevel(@Param("discussionId") UUID discussionId, Pageable pageable);

    @Query(value = "SELECT c FROM ForumComment c LEFT JOIN FETCH c.user "
        + "WHERE c.parentComment.id = :parentId AND c.deletedAt IS NULL",
        countQuery = "SELECT COUNT(c) FROM ForumComment c WHERE c.parentComment.id = :parentId AND c.deletedAt IS NULL")
    Page<ForumComment> findReplies(@Param("parentId") UUID parentId, Pageable pageable);

    Optional<ForumComment> findByIdAndDeletedAtIsNull(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ForumComment c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<ForumComment> findByIdAndDeletedAtIsNullForUpdate(@Param("id") UUID id);

    /** Atomic, like every other counter here: concurrent likes must not collide. */
    @Modifying
    @Query("UPDATE ForumComment c SET c.likeCount = c.likeCount + :delta WHERE c.id = :commentId")
    void adjustLikeCount(@Param("commentId") UUID commentId, @Param("delta") int delta);
}
