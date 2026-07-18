package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.util.List;

/**
 * The Learning Dashboard: overall progress plus a per-topic breakdown across the six
 * canonical topics (each listed even when it has no lessons yet).
 */
public record LiteracyDashboardResponse(
    int totalLessons,
    int completedLessons,
    int literacyXp,
    List<TopicProgress> topics
) {

    public record TopicProgress(String topic, int lessonCount, int completedCount) {}
}
