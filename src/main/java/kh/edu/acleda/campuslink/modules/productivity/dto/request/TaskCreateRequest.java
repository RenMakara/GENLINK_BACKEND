package kh.edu.acleda.campuslink.modules.productivity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record TaskCreateRequest(
    @NotBlank @Size(max = 160) String title,
    String description,
    String priority,
    String category,
    LocalDateTime dueDate,
    LocalDateTime reminderTime
) {
}
