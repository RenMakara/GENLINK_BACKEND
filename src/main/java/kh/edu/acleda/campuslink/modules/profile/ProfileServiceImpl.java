package kh.edu.acleda.campuslink.modules.profile;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.FollowStatus;
import kh.edu.acleda.campuslink.modules.community.repository.FollowRepository;
import kh.edu.acleda.campuslink.modules.profile.dto.ProfileResponse;
import kh.edu.acleda.campuslink.modules.profile.dto.ProfileUpdateRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.PublicProfileResponse;
import kh.edu.acleda.campuslink.modules.profile.entity.UserSetting;
import kh.edu.acleda.campuslink.modules.profile.repository.UserSettingRepository;
import kh.edu.acleda.campuslink.modules.profile.service.InterestService;
import kh.edu.acleda.campuslink.modules.profile.service.PortfolioService;
import kh.edu.acleda.campuslink.modules.profile.service.SocialLinkService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileServiceImpl implements ProfileService {

    private final UserRepository userRepository;
    private final UserSettingRepository userSettingRepository;
    private final FollowRepository followRepository;
    private final PortfolioService portfolioService;
    private final InterestService interestService;
    private final SocialLinkService socialLinkService;
    private final CurrentUserProvider currentUserProvider;

    public ProfileServiceImpl(UserRepository userRepository,
                              UserSettingRepository userSettingRepository,
                              FollowRepository followRepository,
                              PortfolioService portfolioService,
                              InterestService interestService,
                              SocialLinkService socialLinkService,
                              CurrentUserProvider currentUserProvider) {
        this.userRepository = userRepository;
        this.userSettingRepository = userSettingRepository;
        this.followRepository = followRepository;
        this.portfolioService = portfolioService;
        this.interestService = interestService;
        this.socialLinkService = socialLinkService;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public PublicProfileResponse getPublicProfile(String username, Language language) {
        User target = userRepository.findByUsername(username)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
        if (target.getAccountStatus() == AccountStatus.DELETED
            || target.getAccountStatus() == AccountStatus.BANNED
            || target.getDeletedAt() != null) {
            throw new NotFoundException("User not found: " + username);
        }

        UUID viewerId = currentUserProvider.getCurrentUserId();
        if (!target.getId().equals(viewerId)) {
            enforceProfilePrivacy(target.getId(), viewerId);
        }

        return new PublicProfileResponse(
            target.getId(),
            target.getFullName(),
            target.getUsername(),
            target.getAvatarUrl(),
            target.getBio(),
            target.getUniversityName(),
            target.getWorkStatus(),
            target.getLocation(),
            target.getTotalXp(),
            target.getLevel(),
            target.getCreatedAt(),
            portfolioService.getByUserId(target.getId()),
            interestService.getByUserId(target.getId(), language),
            socialLinkService.getByUserId(target.getId()),
            target.getCoverPreset()
        );
    }

    private void enforceProfilePrivacy(UUID targetId, UUID viewerId) {
        String privacy = userSettingRepository.findByUserId(targetId)
            .map(UserSetting::getPrivacyProfile)
            .orElse("PUBLIC");

        if ("PRIVATE".equalsIgnoreCase(privacy)) {
            throw new ApiException("This profile is private", "PROFILE_PRIVATE");
        }
        if ("FRIENDS".equalsIgnoreCase(privacy) && !areFriends(viewerId, targetId)) {
            throw new ApiException("This profile is visible to friends only", "PROFILE_FRIENDS_ONLY");
        }
    }

    /** Friendship is a mutual accepted follow in both directions. */
    private boolean areFriends(UUID viewerId, UUID targetId) {
        return followRepository.existsByFollowerIdAndFollowingIdAndStatus(viewerId, targetId, FollowStatus.ACCEPTED)
            && followRepository.existsByFollowerIdAndFollowingIdAndStatus(targetId, viewerId, FollowStatus.ACCEPTED);
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileResponse getCurrentProfile(String email) {
        return toResponse(loadVisibleUser(email));
    }

    @Override
    @Transactional
    public ProfileResponse updateCurrentProfile(String email, ProfileUpdateRequest request) {
        User user = loadEditableUser(email);

        if (request.username() != null && !request.username().equals(user.getUsername())
            && userRepository.existsByUsername(request.username())) {
            throw new ApiException("Username already registered");
        }
        if (request.phoneNumber() != null && !request.phoneNumber().equals(user.getPhoneNumber())
            && userRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new ApiException("Phone already registered");
        }

        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        if (request.username() != null) {
            user.setUsername(request.username());
        }
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(request.phoneNumber().isBlank() ? null : request.phoneNumber());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl());
        }
        if (request.bio() != null) {
            user.setBio(request.bio());
        }
        if (request.dateOfBirth() != null) {
            user.setDateOfBirth(request.dateOfBirth());
        }
        if (request.gender() != null) {
            user.setGender(request.gender());
        }
        if (request.universityName() != null) {
            user.setUniversityName(request.universityName());
        }
        if (request.workStatus() != null) {
            user.setWorkStatus(request.workStatus());
        }
        if (request.location() != null) {
            user.setLocation(request.location());
        }
        if (request.preferredLanguage() != null) {
            Language language = request.preferredLanguage();
            user.setPreferredLanguage(language);
            userSettingRepository.findByUserId(user.getId()).ifPresent(setting -> {
                setting.setLanguage(language);
                userSettingRepository.save(setting);
            });
        }
        if (request.coverPreset() != null) {
            if (request.coverPreset() < 0 || request.coverPreset() > 9) {
                throw new ApiException("Cover preset must be between 0 and 9");
            }
            user.setCoverPreset(request.coverPreset());
        }

        return toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public ProfileResponse updateAvatar(String email, String avatarUrl) {
        User user = loadEditableUser(email);
        user.setAvatarUrl(avatarUrl);
        return toResponse(userRepository.save(user));
    }

    private User loadVisibleUser(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new NotFoundException("User not found with email: " + email));
        if (user.getAccountStatus() == AccountStatus.DELETED || user.getDeletedAt() != null) {
            throw new NotFoundException("User not found with email: " + email);
        }
        return user;
    }

    private User loadEditableUser(String email) {
        User user = loadVisibleUser(email);
        if (user.getAccountStatus() == AccountStatus.BANNED) {
            throw new ApiException("Your account has been banned");
        }
        return user;
    }

    private ProfileResponse toResponse(User user) {
        return new ProfileResponse(
            user.getId(),
            user.getFullName(),
            user.getUsername(),
            user.getEmail(),
            user.getPhoneNumber(),
            user.getAvatarUrl(),
            user.getBio(),
            user.getDateOfBirth(),
            user.getGender(),
            user.getUniversityName(),
            user.getWorkStatus(),
            user.getLocation(),
            user.isEmailVerified(),
            user.getAccountStatus(),
            user.getTotalXp(),
            user.getLevel(),
            user.getPreferredLanguage(),
            user.getCoverPreset()
        );
    }
}
