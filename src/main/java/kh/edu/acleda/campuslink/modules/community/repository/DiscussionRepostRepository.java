package kh.edu.acleda.campuslink.modules.community.repository;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.entity.DiscussionRepost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiscussionRepostRepository extends JpaRepository<DiscussionRepost, UUID> {

    boolean existsByDiscussionIdAndUserId(UUID discussionId, UUID userId);

    java.util.Optional<DiscussionRepost> findByDiscussionIdAndUserId(UUID discussionId, UUID userId);

    /** Public-profile history, with both sides of the repost loaded for one page query. */
    @Query(value = "SELECT r FROM DiscussionRepost r "
        + "JOIN FETCH r.discussion d LEFT JOIN FETCH d.user "
        + "JOIN FETCH r.user u "
        + "WHERE r.user.id = :userId AND d.deletedAt IS NULL "
        + "ORDER BY r.createdAt DESC",
        countQuery = "SELECT COUNT(r) FROM DiscussionRepost r "
            + "WHERE r.user.id = :userId AND r.discussion.deletedAt IS NULL")
    Page<DiscussionRepost> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM DiscussionRepost r "
        + "WHERE r.discussion.id = :discussionId AND r.user.id = :userId")
    int deleteByDiscussionIdAndUserId(@Param("discussionId") UUID discussionId,
                                      @Param("userId") UUID userId);
}
