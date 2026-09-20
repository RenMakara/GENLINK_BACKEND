package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoryRepository extends JpaRepository<Story, UUID> {

    Optional<Story> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Active stories (not expired, not deleted) for a set of authors, oldest first so a
     * viewer plays a person's stories in order. The author is fetched for the feed header.
     */
    @Query("SELECT s FROM Story s JOIN FETCH s.user "
        + "WHERE s.user.id IN :authorIds AND s.expiresAt > :now AND s.deletedAt IS NULL "
        + "ORDER BY s.createdAt ASC")
    List<Story> findActiveByAuthors(@Param("authorIds") Collection<UUID> authorIds, @Param("now") Instant now);

    /**
     * Active stories visible to the viewer:
     * - The author is in authorIds (people the viewer follows + the viewer).
     * - Visibility rules enforced at the database level:
     *   1) The viewer is the author (viewer always sees own stories regardless of visibility).
     *   2) OR visibility is PUBLIC.
     *   3) OR visibility is FRIENDS and the author is in friendIds (mutual accepted follows).
     */
    @Query("SELECT s FROM Story s JOIN FETCH s.user "
        + "WHERE s.user.id IN :authorIds "
        + "AND s.expiresAt > :now "
        + "AND s.deletedAt IS NULL "
        + "AND ("
        + "    s.user.id = :viewerId "
        + "    OR s.visibility = kh.edu.acleda.campuslink.modules.community.enums.Visibility.PUBLIC "
        + "    OR (s.visibility = kh.edu.acleda.campuslink.modules.community.enums.Visibility.FRIENDS AND s.user.id IN :friendIds)"
        + ") "
        + "ORDER BY s.createdAt ASC")
    List<Story> findVisibleActiveStories(
        @Param("authorIds") Collection<UUID> authorIds,
        @Param("viewerId") UUID viewerId,
        @Param("friendIds") Collection<UUID> friendIds,
        @Param("now") Instant now
    );
}
