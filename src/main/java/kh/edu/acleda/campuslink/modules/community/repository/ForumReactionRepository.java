package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.ForumReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ForumReactionRepository extends JpaRepository<ForumReaction, UUID> {

    Optional<ForumReaction> findByDiscussionIdAndUserId(UUID discussionId, UUID userId);

    /** Batched for a list page: one query, not one per discussion. */
    @Query("SELECT r.discussion.id FROM ForumReaction r "
        + "WHERE r.user.id = :userId AND r.discussion.id IN :discussionIds")
    List<UUID> findReactedDiscussionIds(@Param("userId") UUID userId,
                                        @Param("discussionIds") Collection<UUID> discussionIds);
}
