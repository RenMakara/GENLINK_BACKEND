package kh.edu.acleda.campuslink.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kh.edu.acleda.campuslink.modules.user.entity.WorkStatus;
import kh.edu.acleda.campuslink.shared.enums.Language;

public record RegisterRequest(
    @NotBlank String fullName,
    @NotBlank @Size(max = 60) String username,
    @NotBlank @Email String email,
    String phoneNumber,
    @NotBlank @Size(min = 8, max = 72) String password,
    String universityName,
    WorkStatus workStatus,
    Language preferredLanguage
) {}
