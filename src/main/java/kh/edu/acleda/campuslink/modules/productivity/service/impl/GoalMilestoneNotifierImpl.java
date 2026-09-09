package kh.edu.acleda.campuslink.modules.productivity.service.impl;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.productivity.entity.Goal;
import kh.edu.acleda.campuslink.modules.productivity.entity.GoalMilestone;
import kh.edu.acleda.campuslink.modules.productivity.service.GoalMilestoneNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Builds the stable, navigable notification for a completed goal milestone. */
@Slf4j
@Service
public class GoalMilestoneNotifierImpl implements GoalMilestoneNotifier {

    private static final String TEMPLATE_KEY = "PRODUCTIVITY_GOAL_MILESTONE";
    private static final String NOTIFICATION_TYPE = "PRODUCTIVITY_GOAL";

    private final NotificationEmitter notificationEmitter;

    public GoalMilestoneNotifierImpl(NotificationEmitter notificationEmitter) {
        this.notificationEmitter = notificationEmitter;
    }

    @Override
    public void notifyForCompletedMilestone(Goal goal,
                                            GoalMilestone milestone,
                                            BigDecimal progressPercentage) {
        if (goal == null || milestone == null || progressPercentage == null) {
            return;
        }
        if (goal.getId() == null || goal.getUser() == null || goal.getUser().getId() == null
            || goal.getTitle() == null || goal.getTitle().isBlank()
            || milestone.getId() == null || milestone.getTitle() == null || milestone.getTitle().isBlank()
            || progressPercentage.signum() < 0) {
            log.warn("Skipping goal milestone notification because details are incomplete: goal={} milestone={}",
                goal.getId(), milestone.getId());
            return;
        }

        UUID goalId = goal.getId();
        UUID milestoneId = milestone.getId();
        notificationEmitter.emit(new NotificationRequest(
            goal.getUser().getId(),
            TEMPLATE_KEY,
            NOTIFICATION_TYPE,
            Map.of(
                "goalTitle", goal.getTitle(),
                "milestoneTitle", milestone.getTitle(),
                "progressPercent", progressPercentage.toPlainString()),
            "GOAL",
            goalId,
            "goal-milestone:" + milestoneId));
    }
}
