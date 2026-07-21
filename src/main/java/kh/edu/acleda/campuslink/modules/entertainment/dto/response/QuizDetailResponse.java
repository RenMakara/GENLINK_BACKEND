package kh.edu.acleda.campuslink.modules.entertainment.dto.response;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.enums.QuizDifficulty;

/** Quiz with its questions and options - the `correct` flag is deliberately withheld. */
public record QuizDetailResponse(
    UUID quizId,
    String title,
    String category,
    QuizDifficulty difficulty,
    Integer xpReward,
    List<Question> questions
) {

    public record Question(UUID questionId, String questionText, List<Option> options) {}

    public record Option(UUID optionId, String optionText) {}
}
