package kh.edu.acleda.campuslink.modules.productivity.repository;

/** Database-side counts used by the mobile Productivity dashboard. */
public record TaskDashboardCounts(
    Long total,
    Long completed,
    Long dueToday,
    Long overdue
) {
}
