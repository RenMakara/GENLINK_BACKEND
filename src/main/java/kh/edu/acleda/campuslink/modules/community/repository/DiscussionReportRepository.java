package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.DiscussionReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface DiscussionReportRepository extends JpaRepository<DiscussionReport, UUID> {

    boolean existsByDiscussionIdAndUserId(UUID discussionId, UUID userId);

    /** Clears a discussion's reports when an admin dismisses them. */
    @Modifying
    @Query("DELETE FROM DiscussionReport r WHERE r.discussion.id = :discussionId")
    void deleteByDiscussionId(@Param("discussionId") UUID discussionId);
}
