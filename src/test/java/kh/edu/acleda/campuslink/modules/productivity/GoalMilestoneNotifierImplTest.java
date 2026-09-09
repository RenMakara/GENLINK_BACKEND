package kh.edu.acleda.campuslink.modules.productivity;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.productivity.entity.Goal;
import kh.edu.acleda.campuslink.modules.productivity.entity.GoalMilestone;
import kh.edu.acleda.campuslink.modules.productivity.service.impl.GoalMilestoneNotifierImpl;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GoalMilestoneNotifierImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID GOAL_ID = UUID.randomUUID();
    private static final UUID MILESTONE_ID = UUID.randomUUID();

    @Mock private NotificationEmitter notificationEmitter;

    private GoalMilestoneNotifierImpl notifier;
    private Goal goal;
    private GoalMilestone milestone;

    @BeforeEach
    void setUp() {
        notifier = new GoalMilestoneNotifierImpl(notificationEmitter);
        User user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        goal = Goal.builder().user(user).title("Learn Spring").build();
        ReflectionTestUtils.setField(goal, "id", GOAL_ID);
        milestone = GoalMilestone.builder().goal(goal).title("Finish REST chapter").completed(true).build();
        ReflectionTestUtils.setField(milestone, "id", MILESTONE_ID);
    }

    @Test
    void emitsMilestoneNotificationWithGoalNavigationAndProgress() {
        notifier.notifyForCompletedMilestone(goal, milestone, new BigDecimal("50.00"));

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationEmitter).emit(captor.capture());
        NotificationRequest request = captor.getValue();
        assertThat(request.recipientUserId()).isEqualTo(USER_ID);
        assertThat(request.templateKey()).isEqualTo("PRODUCTIVITY_GOAL_MILESTONE");
        assertThat(request.type()).isEqualTo("PRODUCTIVITY_GOAL");
        assertThat(request.parameters()).containsExactlyInAnyOrderEntriesOf(Map.of(
            "goalTitle", "Learn Spring",
            "milestoneTitle", "Finish REST chapter",
            "progressPercent", "50.00"));
        assertThat(request.referenceType()).isEqualTo("GOAL");
        assertThat(request.referenceId()).isEqualTo(GOAL_ID);
        assertThat(request.dedupeKey()).isEqualTo("goal-milestone:" + MILESTONE_ID);
    }

    @Test
    void skipsIncompleteMilestoneData() {
        GoalMilestone withoutTitle = GoalMilestone.builder().goal(goal).completed(true).build();
        ReflectionTestUtils.setField(withoutTitle, "id", MILESTONE_ID);

        notifier.notifyForCompletedMilestone(goal, withoutTitle, new BigDecimal("50.00"));
        notifier.notifyForCompletedMilestone(goal, milestone, BigDecimal.ZERO.subtract(BigDecimal.ONE));

        verify(notificationEmitter, never()).emit(any(NotificationRequest.class));
    }
}
