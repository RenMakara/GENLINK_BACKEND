package kh.edu.acleda.campuslink.modules.finance.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;

public record QuizSubmitRequest(
    @NotEmpty @Valid List<Answer> answers
) {

    public record Answer(
        @NotNull UUID questionId,
        @NotBlank @Pattern(regexp = "[A-D]") String selectedOption
    ) {}
}
