package kh.edu.acleda.campuslink.modules.entertainment.dto.response;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.enums.QuizDifficulty;

public record QuizResponse(
    UUID quizId,
    String titleEn,
    String titleKm,
    String category,
    QuizDifficulty difficulty,
    Integer xpReward
) {
}
