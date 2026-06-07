package kh.edu.acleda.campuslink.modules.productivity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record HabitCreateRequest(
    @NotBlank @Size(max = 160) String title,
    @Size(max = 80) String category,
    String frequency,
    LocalTime reminderTime
) {
}
