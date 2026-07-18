package kh.edu.acleda.campuslink.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 6, max = 6) @Pattern(regexp = "\\d{6}", message = "must be 6 digits") String otp,
    @NotBlank @Size(min = 8, max = 72) String newPassword
) {}
