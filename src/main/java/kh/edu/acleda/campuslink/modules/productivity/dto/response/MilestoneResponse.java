package kh.edu.acleda.campuslink.modules.productivity.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record MilestoneResponse(
    UUID milestoneId,
    String title,
    boolean completed,
    LocalDateTime completedAt
) {
}
