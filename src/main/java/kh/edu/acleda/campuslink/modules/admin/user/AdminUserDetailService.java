package kh.edu.acleda.campuslink.modules.admin.user;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.admin.sanction.entity.UserSanction;
import kh.edu.acleda.campuslink.modules.admin.sanction.repository.UserSanctionRepository;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserDetailResponse;
import kh.edu.acleda.campuslink.modules.profile.dto.response.InterestResponse;
import kh.edu.acleda.campuslink.modules.profile.dto.response.SocialLinkResponse;
import kh.edu.acleda.campuslink.modules.profile.service.InterestService;
import kh.edu.acleda.campuslink.modules.profile.service.PortfolioService;
import kh.edu.acleda.campuslink.modules.profile.service.SocialLinkService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only aggregation service for the admin user detail page. */
@Service
@RequiredArgsConstructor
public class AdminUserDetailService {

    private final UserRepository userRepository;
    private final AdminUserRepository adminUserRepository;
    private final InterestService interestService;
    private final SocialLinkService socialLinkService;
    private final PortfolioService portfolioService;
    private final UserSanctionRepository userSanctionRepository;

    @Transactional(readOnly = true)
    public AdminUserDetailResponse get(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        Language language = user.getPreferredLanguage() == null
            ? Language.ENGLISH
            : user.getPreferredLanguage();
        List<AdminUserDetailResponse.Interest> interests = interestService
            .getByUserId(userId, language)
            .stream()
            .map(AdminUserDetailService::toInterest)
            .toList();
        List<AdminUserDetailResponse.SocialLink> socialLinks = socialLinkService
            .getByUserId(userId)
            .stream()
            .map(AdminUserDetailService::toSocialLink)
            .toList();

        AdminUser adminUser = adminUserRepository.findByUserIdAndDeletedAtIsNull(userId).orElse(null);
        String adminRoleName = adminUser == null || adminUser.getAdminRole() == null
            ? null
            : adminUser.getAdminRole().getName();
        Instant now = Instant.now();
        AdminUserDetailResponse.CurrentSanction currentSanction = userSanctionRepository
            .findByUserIdAndLiftedAtIsNullAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
            .stream()
            .filter(sanction -> sanction.isInForce(now))
            .findFirst()
            .map(AdminUserDetailService::toCurrentSanction)
            .orElse(null);

        return new AdminUserDetailResponse(
            user.getId(),
            user.getFullName(),
            user.getUsername(),
            user.getAvatarUrl(),
            user.getBio(),
            user.getDateOfBirth(),
            user.getGender(),
            user.getPhoneNumber(),
            user.getLocation(),
            user.getUniversityName(),
            user.getWorkStatus(),
            language,
            user.getCoverPreset(),
            interests,
            socialLinks,
            portfolioService.countByUserId(userId),
            user.getEmail(),
            user.isEmailVerified(),
            user.getAuthProvider(),
            user.getGoogleId() != null && !user.getGoogleId().isBlank(),
            user.getAccountStatus(),
            user.getCreatedAt(),
            user.getUpdatedAt(),
            user.getDeletedAt(),
            user.getTokenVersion(),
            user.getRole(),
            user.getTotalXp(),
            user.getLevel(),
            adminRoleName,
            currentSanction
        );
    }

    private static AdminUserDetailResponse.Interest toInterest(InterestResponse interest) {
        return new AdminUserDetailResponse.Interest(
            interest.interestId(), interest.code(), interest.name(), interest.category());
    }

    private static AdminUserDetailResponse.SocialLink toSocialLink(SocialLinkResponse link) {
        return new AdminUserDetailResponse.SocialLink(link.platform(), link.url());
    }

    private static AdminUserDetailResponse.CurrentSanction toCurrentSanction(UserSanction sanction) {
        return new AdminUserDetailResponse.CurrentSanction(
            sanction.getType(),
            sanction.getSeverity(),
            sanction.getReason(),
            sanction.getPreviousStatus(),
            sanction.getStartsAt(),
            sanction.getExpiresAt(),
            sanction.getAcknowledgedAt()
        );
    }
}
