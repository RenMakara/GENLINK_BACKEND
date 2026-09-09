package kh.edu.acleda.campuslink.modules.productivity.service;

import java.time.LocalDate;
import kh.edu.acleda.campuslink.modules.productivity.entity.Habit;

/** Emits an in-app update after a habit check-in extends or starts a current streak. */
public interface HabitStreakNotifier {

    void notifyForCompletedCheckIn(Habit habit, LocalDate checkInDate, int streakDays);
}
