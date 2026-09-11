package kh.edu.acleda.campuslink.modules.finance.service.impl;

import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.entity.SavingGoal;
import kh.edu.acleda.campuslink.modules.finance.enums.SavingGoalStatus;
import kh.edu.acleda.campuslink.modules.finance.service.SavingGoalCompletionNotifier;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SavingGoalCompletionNotifierImpl implements SavingGoalCompletionNotifier {

    private static final String TEMPLATE_KEY = "FINANCE_SAVING_GOAL_REACHED";
    private static final String NOTIFICATION_TYPE = "FINANCE_SAVING_GOAL";

    private final NotificationEmitter notificationEmitter;

    public SavingGoalCompletionNotifierImpl(NotificationEmitter notificationEmitter) {
        this.notificationEmitter = notificationEmitter;
    }

    @Override
    public void notifyIfCompleted(SavingGoal goal, SavingGoalStatus statusBefore) {
        if (goal == null || goal.getStatus() != SavingGoalStatus.COMPLETED
            || statusBefore == SavingGoalStatus.COMPLETED) {
            return;
        }
        if (goal.getId() == null || goal.getUser() == null || goal.getUser().getId() == null
            || goal.getTitle() == null || goal.getTitle().isBlank()) {
            log.warn("Skipping saving-goal completion notification because details are incomplete: goal={}",
                goal == null ? null : goal.getId());
            return;
        }

        UUID userId = goal.getUser().getId();
        UUID goalId = goal.getId();
        notificationEmitter.emit(new NotificationRequest(
            userId,
            TEMPLATE_KEY,
            NOTIFICATION_TYPE,
            Map.of("goalName", goal.getTitle()),
            "SAVING_GOAL",
            goalId,
            "saving-goal-reached:" + goalId));
    }
}
