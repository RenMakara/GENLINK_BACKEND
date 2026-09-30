package kh.edu.acleda.campuslink.modules.entertainment.dto.response;

import java.util.List;
import java.util.UUID;

/** @param score percent correct (0-100) */
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
