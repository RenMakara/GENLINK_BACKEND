package kh.edu.acleda.campuslink.modules.productivity.dto.response;

/** Aggregates over completed focus sessions. */
public record FocusStatsResponse(
    int todaySessions,
    int todayMinutes,
    long totalSessions,
    long totalMinutes,
    long totalXp
) {
}
