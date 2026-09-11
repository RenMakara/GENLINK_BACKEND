package kh.edu.acleda.campuslink.modules.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.entity.SavingGoal;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.SavingGoalStatus;
import kh.edu.acleda.campuslink.modules.finance.service.impl.SavingGoalCompletionNotifierImpl;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SavingGoalCompletionNotifierImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID GOAL_ID = UUID.randomUUID();

    @Mock private NotificationEmitter notificationEmitter;

    private SavingGoalCompletionNotifierImpl notifier;
    private SavingGoal goal;

    @BeforeEach
    void setUp() {
        notifier = new SavingGoalCompletionNotifierImpl(notificationEmitter);
        User user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        goal = SavingGoal.builder().user(user).title("Laptop").currency(Currency.KHR)
            .targetAmount(new BigDecimal("1000000.00")).currentAmount(new BigDecimal("1000000.00"))
            .status(SavingGoalStatus.COMPLETED).build();
        ReflectionTestUtils.setField(goal, "id", GOAL_ID);
    }

    @Test
    void emitsReachedNotificationForACompletionTransition() {
        notifier.notifyIfCompleted(goal, SavingGoalStatus.IN_PROGRESS);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationEmitter).emit(captor.capture());
        NotificationRequest request = captor.getValue();
        assertThat(request.recipientUserId()).isEqualTo(USER_ID);
        assertThat(request.templateKey()).isEqualTo("FINANCE_SAVING_GOAL_REACHED");
        assertThat(request.type()).isEqualTo("FINANCE_SAVING_GOAL");
        assertThat(request.parameters()).containsExactlyInAnyOrderEntriesOf(Map.of("goalName", "Laptop"));
        assertThat(request.referenceType()).isEqualTo("SAVING_GOAL");
        assertThat(request.referenceId()).isEqualTo(GOAL_ID);
        assertThat(request.dedupeKey()).isEqualTo("saving-goal-reached:" + GOAL_ID);
    }

    @Test
    void doesNotRepeatNotificationForAnAlreadyCompletedGoal() {
        notifier.notifyIfCompleted(goal, SavingGoalStatus.COMPLETED);

        verify(notificationEmitter, never()).emit(any(NotificationRequest.class));
    }
}
