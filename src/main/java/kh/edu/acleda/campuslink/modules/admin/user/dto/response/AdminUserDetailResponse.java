package kh.edu.acleda.campuslink.modules.admin.user.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionSeverity;
import kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionType;
import kh.edu.acleda.campuslink.modules.profile.enums.SocialPlatform;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.AuthProvider;
import kh.edu.acleda.campuslink.modules.user.entity.Gender;
import kh.edu.acleda.campuslink.modules.user.entity.Role;
import kh.edu.acleda.campuslink.modules.user.entity.WorkStatus;
import kh.edu.acleda.campuslink.shared.enums.Language;

/** Admin-only user detail projection for the Profile and Account tabs. */
public record AdminUserDetailResponse(
    UUID userId,
    String fullName,
    String username,
    String avatarUrl,
    String bio,
    LocalDate dateOfBirth,
    Gender gender,
    String phoneNumber,
    String location,
    String universityName,
    WorkStatus workStatus,
    Language preferredLanguage,
    int coverPreset,
    List<Interest> interests,
    List<SocialLink> socialLinks,
    long portfolioCount,
    String email,
    boolean emailVerified,
    AuthProvider authProvider,
    boolean googleLinked,
    AccountStatus accountStatus,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt,
    int tokenVersion,
    Role mobileRole,
    int totalXp,
    int level,
    String adminRoleName,
    CurrentSanction currentSanction
) {

    public record Interest(
        UUID interestId,
        String code,
        String name,
        String category
    ) {
    }

    public record SocialLink(
        SocialPlatform platform,
        String url
    ) {
    }

    /** The currently effective warning, suspension, or ban, if one exists. */
    public record CurrentSanction(
        SanctionType type,
        SanctionSeverity severity,
        String reason,
        AccountStatus previousStatus,
        Instant startsAt,
        Instant expiresAt,
        Instant acknowledgedAt
    ) {
    }
}
