package kh.edu.acleda.campuslink.modules.productivity.service;

import java.math.BigDecimal;
import kh.edu.acleda.campuslink.modules.productivity.entity.Goal;
import kh.edu.acleda.campuslink.modules.productivity.entity.GoalMilestone;

/** Emits an in-app notification after a goal milestone becomes completed. */
public interface GoalMilestoneNotifier {

    void notifyForCompletedMilestone(Goal goal, GoalMilestone milestone, BigDecimal progressPercentage);
}
