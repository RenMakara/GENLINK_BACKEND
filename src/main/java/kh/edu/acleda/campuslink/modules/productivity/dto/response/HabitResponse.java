package kh.edu.acleda.campuslink.modules.productivity.dto.response;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

/** streakCount, completionRate and checkedInToday are computed from habit_check_ins on read. */
public record HabitResponse(
    UUID habitId,
    String title,
    String category,
    String frequency,
    LocalTime reminderTime,
    int streakCount,
    BigDecimal completionRate,
    boolean checkedInToday,
    boolean checkedInThisWeek,
    int xpEarned
) {
}
