package kh.edu.acleda.campuslink.modules.productivity.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * progressPercentage and status are computed from the goal's milestones on read
 * (done/total), not read from the stored columns - so they never drift.
 *
 * @param status NOT_STARTED / IN_PROGRESS / COMPLETED
 */
public record GoalResponse(
    UUID goalId,
    String title,
    String description,
    String goalType,
    LocalDate deadline,
    BigDecimal progressPercentage,
    String status,
    int totalMilestones,
    int completedMilestones,
    List<MilestoneResponse> milestones
) {
}
