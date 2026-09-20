package kh.edu.acleda.campuslink.modules.security.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetPasswordRequest(
    @NotBlank @Size(min = 8, max = 72) String password
) {
}
