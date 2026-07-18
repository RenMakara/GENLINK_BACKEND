package kh.edu.acleda.campuslink.modules.entertainment.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/** One past attempt, for the history list. */
public record QuizAttemptSummaryResponse(
    UUID attemptId,
    UUID quizId,
    String quizTitle,
    int score,
    int correctCount,
    int totalQuestions,
    int xpEarned,
    LocalDateTime completedAt
) {
}
