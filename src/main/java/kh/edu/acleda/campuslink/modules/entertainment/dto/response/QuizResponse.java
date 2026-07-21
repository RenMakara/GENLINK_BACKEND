package kh.edu.acleda.campuslink.modules.entertainment.dto.response;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.enums.QuizDifficulty;

/** Quiz summary for the list; title is localized. */
public record QuizResponse(
    UUID quizId,
    String title,
    String category,
    QuizDifficulty difficulty,
    Integer xpReward,
    long questionCount
) {
}
