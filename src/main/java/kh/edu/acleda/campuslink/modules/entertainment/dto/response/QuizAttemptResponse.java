package kh.edu.acleda.campuslink.modules.entertainment.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * @param score    percent correct (0-100)
 * @param xpEarned XP granted by this attempt - 0 on a replay (XP is once per quiz)
 */
public record QuizAttemptResponse(
    UUID attemptId,
    int score,
    int correctCount,
    int totalQuestions,
    int xpEarned,
    List<AnswerResult> results
) {

    public record AnswerResult(
        UUID questionId,
        UUID selectedOptionId,
        UUID correctOptionId,
        boolean correct
    ) {}
}
