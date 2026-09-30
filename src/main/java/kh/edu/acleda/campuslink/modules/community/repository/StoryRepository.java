package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;

public interface StoryRepository extends JpaRepository<Story, UUID> {

    Optional<Story> findByIdAndDeletedAtIsNull(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Story s WHERE s.id = :id AND s.deletedAt IS NULL")
    Optional<Story> findByIdAndDeletedAtIsNullForUpdate(@Param("id") UUID id);

    /** Active stories (not expired, not deleted) for a set of authors, oldest first so a viewer plays a person's stories in order. */
    @Query("SELECT s FROM Story s JOIN FETCH s.user "
        + "WHERE s.user.id IN :authorIds AND s.expiresAt > :now AND s.deletedAt IS NULL "
        + "ORDER BY s.createdAt ASC")
    List<Story> findActiveByAuthors(@Param("authorIds") Collection<UUID> authorIds, @Param("now") Instant now);

    /** Active stories visible to the viewer: - The author is in authorIds (people the viewer follows + the viewer). */
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
