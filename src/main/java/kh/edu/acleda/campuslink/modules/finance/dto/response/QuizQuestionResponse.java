package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.util.List;
import java.util.UUID;

/** A quiz question as shown to the user - the correct answer is deliberately absent. */
public record QuizQuestionResponse(
    UUID questionId,
    String questionText,
    List<Option> options
) {

    /** key is the option letter ("A".."D"); only present options are included. */
    public record Option(String key, String text) {}
}
