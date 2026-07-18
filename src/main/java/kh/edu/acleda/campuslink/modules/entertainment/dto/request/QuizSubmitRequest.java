package kh.edu.acleda.campuslink.modules.entertainment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record QuizSubmitRequest(
    @NotEmpty @Valid List<Answer> answers
) {

    public record Answer(
        @NotNull UUID questionId,
        @NotNull UUID selectedOptionId
    ) {}
}
