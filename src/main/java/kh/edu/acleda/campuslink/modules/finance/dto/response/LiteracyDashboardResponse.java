package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.util.List;

/** Learning progress by topic and overall. */
public record LiteracyDashboardResponse(
    int totalLessons,
    int completedLessons,
    int literacyXp,
    List<TopicProgress> topics
) {

    public record TopicProgress(String topic, int lessonCount, int completedCount) {}
}
