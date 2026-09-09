package kh.edu.acleda.campuslink.modules.finance.service;

import kh.edu.acleda.campuslink.modules.finance.entity.SavingGoal;
import kh.edu.acleda.campuslink.modules.finance.enums.SavingGoalStatus;

/** Emits the one-time in-app notification for a saving goal completing. */
public interface SavingGoalCompletionNotifier {

    void notifyIfCompleted(SavingGoal goal, SavingGoalStatus statusBefore);
}
