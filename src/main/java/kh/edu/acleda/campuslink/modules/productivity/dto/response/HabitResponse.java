package kh.edu.acleda.campuslink.modules.productivity.dto.response;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

/**
 * streakCount, completionRate and checkedInToday are computed from habit_check_ins on
 * read, not taken from the stored counters - so they never drift.
 *
 * @param streakCount    consecutive completed days ending today or yesterday
 * @param completionRate completed days over the last 30, as a percent (0-100)
 */
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
