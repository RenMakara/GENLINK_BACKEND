package kh.edu.acleda.campuslink.modules.mobile.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The Productivity screen in one call (BFF-lite). Every section degrades independently:
 * a new user gets a well-formed body with zero counts, empty lists and a null motivation.
 */
public record MobileProductivityDashboardResponse(
    HabitsToday habits,
    TasksSummary tasks,
    List<GoalProgress> goals,
    FocusSummary focus,
    MotivationCard motivation
) {

    public record HabitsToday(int total, int checkedInToday, List<HabitItem> items) {}

    public record HabitItem(UUID habitId, String title, boolean checkedInToday) {}

    public record TasksSummary(long total, long completed, int dueToday, int overdue) {}

    public record GoalProgress(UUID goalId, String title, BigDecimal progressPercentage, String status) {}

    public record FocusSummary(int todaySessions, int todayMinutes, long totalXp) {}

    public record MotivationCard(UUID motivationId, String title, String content) {}
}
