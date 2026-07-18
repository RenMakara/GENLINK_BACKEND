package kh.edu.acleda.campuslink.modules.productivity.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record FocusSessionStartRequest(
    @Size(max = 50) String mode,
    @NotNull @Positive Integer durationMinutes,
    @Size(max = 80) String ambientSound
) {
}
