package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.DiscussionReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DiscussionReportRepository extends JpaRepository<DiscussionReport, UUID> {

    boolean existsByDiscussionIdAndUserId(UUID discussionId, UUID userId);
}
