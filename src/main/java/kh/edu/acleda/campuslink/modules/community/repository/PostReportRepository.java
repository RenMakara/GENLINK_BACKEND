package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.PostReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface PostReportRepository extends JpaRepository<PostReport, UUID> {

    boolean existsByPostIdAndUserId(UUID postId, UUID userId);

    /** Clears a post's reports when a moderator dismisses them. */
    @Modifying
    @Query("DELETE FROM PostReport r WHERE r.post.id = :postId")
    void deleteByPostId(@Param("postId") UUID postId);
}
