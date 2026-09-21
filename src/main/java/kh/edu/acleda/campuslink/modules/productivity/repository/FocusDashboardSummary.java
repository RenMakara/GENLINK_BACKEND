package kh.edu.acleda.campuslink.modules.productivity.repository;

/** Database-side aggregates used by the mobile Productivity dashboard. */
public record FocusDashboardSummary(
    Long todaySessions,
    Long todayFocusedSeconds,
    Long totalXp
) {
}
