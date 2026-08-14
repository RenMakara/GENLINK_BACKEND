package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.StoryView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StoryViewRepository extends JpaRepository<StoryView, UUID> {

    boolean existsByStoryIdAndViewerId(UUID storyId, UUID viewerId);

    /** Which of these stories the viewer has already seen, to resolve hasUnseen in one query. */
    @Query("SELECT v.story.id FROM StoryView v WHERE v.viewer.id = :viewerId AND v.story.id IN :storyIds")
    List<UUID> findViewedStoryIds(@Param("viewerId") UUID viewerId, @Param("storyIds") Collection<UUID> storyIds);

    /** The owner's viewer list for one story, most recent first; the viewer is fetched for display. */
    @Query("SELECT v FROM StoryView v JOIN FETCH v.viewer WHERE v.story.id = :storyId ORDER BY v.viewedAt DESC")
    List<StoryView> findViewersOfStory(@Param("storyId") UUID storyId);
}
