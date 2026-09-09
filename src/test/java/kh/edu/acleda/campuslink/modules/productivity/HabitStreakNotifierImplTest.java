package kh.edu.acleda.campuslink.modules.productivity;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.productivity.entity.Habit;
import kh.edu.acleda.campuslink.modules.productivity.service.impl.HabitStreakNotifierImpl;
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
class HabitStreakNotifierImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID HABIT_ID = UUID.randomUUID();
    private static final LocalDate CHECK_IN_DATE = LocalDate.of(2026, 9, 9);

    @Mock private NotificationEmitter notificationEmitter;

    private HabitStreakNotifierImpl notifier;
    private Habit habit;

    @BeforeEach
    void setUp() {
        notifier = new HabitStreakNotifierImpl(notificationEmitter);
        User user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        habit = Habit.builder().user(user).title("Read").build();
        ReflectionTestUtils.setField(habit, "id", HABIT_ID);
    }

    @Test
    void emitsAStreakNotificationForACompletedCheckIn() {
        notifier.notifyForCompletedCheckIn(habit, CHECK_IN_DATE, 3);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationEmitter).emit(captor.capture());
        NotificationRequest request = captor.getValue();
        assertThat(request.recipientUserId()).isEqualTo(USER_ID);
        assertThat(request.templateKey()).isEqualTo("PRODUCTIVITY_HABIT_STREAK");
        assertThat(request.type()).isEqualTo("PRODUCTIVITY_HABIT");
        assertThat(request.parameters()).containsExactlyInAnyOrderEntriesOf(Map.of(
            "habitName", "Read", "streakDays", "3"));
        assertThat(request.referenceType()).isEqualTo("HABIT");
        assertThat(request.referenceId()).isEqualTo(HABIT_ID);
        assertThat(request.dedupeKey()).isEqualTo("habit-streak:" + HABIT_ID + ":" + CHECK_IN_DATE);
    }

    @Test
    void skipsAZeroStreakOrIncompleteHabit() {
        notifier.notifyForCompletedCheckIn(habit, CHECK_IN_DATE, 0);

        Habit withoutUser = Habit.builder().title("Read").build();
        ReflectionTestUtils.setField(withoutUser, "id", HABIT_ID);
        notifier.notifyForCompletedCheckIn(withoutUser, CHECK_IN_DATE, 1);

        verify(notificationEmitter, never()).emit(any(NotificationRequest.class));
    }
}
