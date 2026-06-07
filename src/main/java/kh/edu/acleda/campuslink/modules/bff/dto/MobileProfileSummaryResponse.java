package kh.edu.acleda.campuslink.modules.bff.dto;

import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.WorkStatus;
import kh.edu.acleda.campuslink.shared.enums.Language;

import java.util.UUID;

public record MobileProfileSummaryResponse(
    UUID userId,
    String fullName,
    String username,
    String email,
    String phoneNumber,
    String avatarUrl,
    String bio,
    String universityName,
    WorkStatus workStatus,
    String location,
    boolean emailVerified,
    AccountStatus accountStatus,
    int totalXp,
    int level,
    Language preferredLanguage
) {
}
