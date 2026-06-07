package kh.edu.acleda.campuslink.modules.profile.dto;

import jakarta.validation.constraints.Size;
import kh.edu.acleda.campuslink.modules.user.entity.Gender;
import kh.edu.acleda.campuslink.modules.user.entity.WorkStatus;
import kh.edu.acleda.campuslink.shared.enums.Language;

import java.time.LocalDate;

public record ProfileUpdateRequest(
    @Size(max = 120) String fullName,
    @Size(max = 60) String username,
    @Size(max = 30) String phoneNumber,
    String avatarUrl,
    String bio,
    LocalDate dateOfBirth,
    Gender gender,
    @Size(max = 180) String universityName,
    WorkStatus workStatus,
    @Size(max = 120) String location,
    Language preferredLanguage
) {
}
