package kh.edu.acleda.campuslink.modules.productivity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record GoalCreateRequest(
    @NotBlank @Size(max = 160) String title,
    String description,
    String goalType,
    LocalDate deadline
) {
}
