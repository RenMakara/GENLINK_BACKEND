package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.util.UUID;

public record LessonSummaryResponse(
    UUID lessonId,
    String title,
    String topic,
    String contentType,
    Integer estimatedTime,
    int xpReward,
    boolean completed,
    boolean quizPassed
) {
}
