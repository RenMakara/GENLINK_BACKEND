package kh.edu.acleda.campuslink.modules.community.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.entity.StoryReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoryReactionRepository extends JpaRepository<StoryReaction, UUID> {

    Optional<StoryReaction> findByStoryIdAndUserId(UUID storyId, UUID userId);

    long countByStoryId(UUID storyId);

    @Query("SELECT r.story.id, COUNT(r) FROM StoryReaction r "
        + "WHERE r.story.id IN :storyIds GROUP BY r.story.id")
    List<Object[]> countByStoryIds(@Param("storyIds") Collection<UUID> storyIds);

    /** Batched for the story feed so each story can expose reactedByMe. */
    @Query("SELECT r.story.id FROM StoryReaction r "
        + "WHERE r.user.id = :userId AND r.story.id IN :storyIds")
    List<UUID> findReactedStoryIds(@Param("userId") UUID userId,
                                   @Param("storyIds") Collection<UUID> storyIds);
}
