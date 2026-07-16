package kh.edu.acleda.campuslink.modules.profile.service.impl;

import java.util.Set;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.profile.dto.request.UserSettingUpdateRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.response.UserSettingResponse;
import kh.edu.acleda.campuslink.modules.profile.entity.UserSetting;
import kh.edu.acleda.campuslink.modules.profile.repository.UserSettingRepository;
import kh.edu.acleda.campuslink.modules.profile.service.UserSettingService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.enums.Theme;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSettingServiceImpl implements UserSettingService {

    private static final Set<String> ALLOWED_PRIVACY = Set.of("PUBLIC", "FRIENDS", "PRIVATE");

    private final UserSettingRepository userSettingRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public UserSettingServiceImpl(UserSettingRepository userSettingRepository,
                                  UserRepository userRepository,
                                  CurrentUserProvider currentUserProvider) {
        this.userSettingRepository = userSettingRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public UserSettingResponse getMine() {
        return toResponse(loadOrCreateForCurrentUser());
    }

    @Override
    @Transactional
    public UserSettingResponse update(UserSettingUpdateRequest request) {
        UserSetting setting = loadOrCreateForCurrentUser();

        if (request.language() != null) {
            setting.setLanguage(request.language());
        }
        if (request.theme() != null) {
            setting.setTheme(request.theme());
        }
        if (request.notificationEnabled() != null) {
            setting.setNotificationEnabled(request.notificationEnabled());
        }
        if (request.privacyProfile() != null) {
            String privacy = request.privacyProfile().toUpperCase();
            if (!ALLOWED_PRIVACY.contains(privacy)) {
                throw new ApiException("Profile privacy must be one of PUBLIC, FRIENDS or PRIVATE", "VALIDATION_ERROR");
            }
            setting.setPrivacyProfile(privacy);
        }
        if (request.allowMessages() != null) {
            setting.setAllowMessages(request.allowMessages());
        }
        if (request.allowFollowRequest() != null) {
            setting.setAllowFollowRequest(request.allowFollowRequest());
        }
        if (request.biometricEnabled() != null) {
            setting.setBiometricEnabled(request.biometricEnabled());
        }

        return toResponse(userSettingRepository.save(setting));
    }

    /**
     * Rows are created lazily so users who registered before this feature existed
     * get defaults on first read instead of a 404.
     */
    private UserSetting loadOrCreateForCurrentUser() {
        UUID userId = currentUserProvider.getCurrentUserId();
        return userSettingRepository.findByUserId(userId)
            .orElseGet(() -> userSettingRepository.save(defaultsFor(userId)));
    }

    private UserSetting defaultsFor(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));
        return UserSetting.builder()
            .user(user)
            // Never null: the field initialises to ENGLISH, the setter coerces null to
            // ENGLISH, and the column is NOT NULL DEFAULT 'ENGLISH'. Settings inherit
            // the user's language rather than re-deciding a default here.
            .language(user.getPreferredLanguage())
            .theme(Theme.SYSTEM_DEFAULT)
            .notificationEnabled(true)
            .privacyProfile("PUBLIC")
            .allowMessages(true)
            .allowFollowRequest(true)
            .biometricEnabled(false)
            .build();
    }

    private UserSettingResponse toResponse(UserSetting setting) {
        return new UserSettingResponse(
            setting.getId(),
            setting.getLanguage(),
            setting.getTheme(),
            setting.getNotificationEnabled(),
            setting.getPrivacyProfile(),
            setting.getAllowMessages(),
            setting.getAllowFollowRequest(),
            setting.getBiometricEnabled()
        );
    }
}
