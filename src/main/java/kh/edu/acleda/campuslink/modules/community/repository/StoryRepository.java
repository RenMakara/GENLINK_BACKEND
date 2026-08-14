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
}
