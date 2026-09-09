package kh.edu.acleda.campuslink.modules.productivity.service.impl;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.productivity.entity.Habit;
import kh.edu.acleda.campuslink.modules.productivity.service.HabitStreakNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Builds the stable, navigable notification for a newly completed habit day. */
@Slf4j
@Service
public class HabitStreakNotifierImpl implements HabitStreakNotifier {

    private static final String TEMPLATE_KEY = "PRODUCTIVITY_HABIT_STREAK";
    private static final String NOTIFICATION_TYPE = "PRODUCTIVITY_HABIT";

    private final NotificationEmitter notificationEmitter;

    public HabitStreakNotifierImpl(NotificationEmitter notificationEmitter) {
        this.notificationEmitter = notificationEmitter;
    }

    @Override
    public void notifyForCompletedCheckIn(Habit habit, LocalDate checkInDate, int streakDays) {
        if (habit == null || checkInDate == null || streakDays <= 0) {
            return;
        }
        if (habit.getId() == null || habit.getUser() == null || habit.getUser().getId() == null
            || habit.getTitle() == null || habit.getTitle().isBlank()) {
            log.warn("Skipping habit streak notification because details are incomplete: habit={}",
                habit == null ? null : habit.getId());
            return;
        }

        UUID habitId = habit.getId();
        notificationEmitter.emit(new NotificationRequest(
            habit.getUser().getId(),
            TEMPLATE_KEY,
            NOTIFICATION_TYPE,
            Map.of(
                "habitName", habit.getTitle(),
                "streakDays", Integer.toString(streakDays)),
            "HABIT",
            habitId,
            "habit-streak:" + habitId + ":" + checkInDate));
    }
}
