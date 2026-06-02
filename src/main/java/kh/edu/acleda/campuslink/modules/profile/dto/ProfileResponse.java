package kh.edu.acleda.campuslink.modules.profile.dto;

import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.Gender;
import kh.edu.acleda.campuslink.modules.user.entity.WorkStatus;

import java.time.LocalDate;
import java.util.UUID;

public record ProfileResponse(
    UUID userId,
    String fullName,
    String username,
    String email,
    String phoneNumber,
    String avatarUrl,
    String bio,
    LocalDate dateOfBirth,
    Gender gender,
    String universityName,
    WorkStatus workStatus,
    String location,
    boolean emailVerified,
    AccountStatus accountStatus,
    int totalXp,
    int level
) {
}
