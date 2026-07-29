package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.ForumCommentReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ForumCommentReactionRepository extends JpaRepository<ForumCommentReaction, UUID> {

    Optional<ForumCommentReaction> findByCommentIdAndUserId(UUID commentId, UUID userId);

    /** Batched for a list page: one query, not one per comment. */
    @Query("SELECT r.comment.id FROM ForumCommentReaction r "
        + "WHERE r.user.id = :userId AND r.comment.id IN :commentIds")
    List<UUID> findReactedCommentIds(@Param("userId") UUID userId,
                                     @Param("commentIds") Collection<UUID> commentIds);
}
