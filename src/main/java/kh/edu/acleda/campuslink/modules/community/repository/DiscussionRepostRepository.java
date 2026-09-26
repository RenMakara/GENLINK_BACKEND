package kh.edu.acleda.campuslink.modules.community.repository;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.entity.DiscussionRepost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiscussionRepostRepository extends JpaRepository<DiscussionRepost, UUID> {

    boolean existsByDiscussionIdAndUserId(UUID discussionId, UUID userId);

    java.util.Optional<DiscussionRepost> findByDiscussionIdAndUserId(UUID discussionId, UUID userId);

    @Modifying
    @Query("DELETE FROM DiscussionRepost r "
        + "WHERE r.discussion.id = :discussionId AND r.user.id = :userId")
    int deleteByDiscussionIdAndUserId(@Param("discussionId") UUID discussionId,
                                      @Param("userId") UUID userId);
}
