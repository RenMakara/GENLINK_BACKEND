package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** @param score percent correct (0-100) */
public record QuizResultResponse(
    int score,
    int correctCount,
    int totalQuestions,
    boolean passed,
    int xpEarned,
    BigDecimal learningBudgetCredited,
    List<QuestionResult> results
) {

    public record QuestionResult(
        UUID questionId,
        String selectedOption,
        String correctOption,
        boolean correct,
        String explanation
    ) {}
}
