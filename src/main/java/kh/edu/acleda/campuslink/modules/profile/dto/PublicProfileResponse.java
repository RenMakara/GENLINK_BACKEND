package kh.edu.acleda.campuslink.modules.profile.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.profile.dto.response.InterestResponse;
import kh.edu.acleda.campuslink.modules.profile.dto.response.PortfolioItemResponse;
import kh.edu.acleda.campuslink.modules.profile.dto.response.SocialLinkResponse;
import kh.edu.acleda.campuslink.modules.user.entity.WorkStatus;

/**
 * Public-safe view of a profile. Deliberately omits email, phone, date of birth,
 * gender and account status, which belong to the owner-only ProfileResponse.
 */
public record PublicProfileResponse(
    UUID userId,
    String fullName,
    String username,
    String avatarUrl,
    String bio,
    String universityName,
    WorkStatus workStatus,
    String location,
    int totalXp,
    int level,
    Instant joinedAt,
    List<PortfolioItemResponse> portfolio,
    List<InterestResponse> interests,
    List<SocialLinkResponse> socialLinks,
    int coverPreset
) {
}
