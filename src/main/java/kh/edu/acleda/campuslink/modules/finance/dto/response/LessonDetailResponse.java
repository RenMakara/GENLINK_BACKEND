package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.util.UUID;

public record LessonDetailResponse(
    UUID lessonId,
    String title,
    String content,
    String topic,
    String contentType,
    String mediaUrl,
    Integer estimatedTime,
    int xpReward,
    boolean completed,
    boolean quizPassed,
    int questionCount
) {
}
