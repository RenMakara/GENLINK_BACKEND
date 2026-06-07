package kh.edu.acleda.campuslink.modules.profile.service.impl;

import kh.edu.acleda.campuslink.modules.profile.dto.request.UserSettingUpdateRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.response.UserSettingResponse;
import kh.edu.acleda.campuslink.modules.profile.service.UserSettingService;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.enums.Theme;
import org.springframework.stereotype.Service;

@Service
public class UserSettingServiceImpl implements UserSettingService {

    @Override
    public UserSettingResponse getMine() {
        // TODO: Load authenticated user's settings, creating defaults when missing.
        return new UserSettingResponse(null, Language.ENGLISH, Theme.SYSTEM_DEFAULT, true, "PUBLIC", true, true, false);
    }

    @Override
    public UserSettingResponse update(UserSettingUpdateRequest request) {
        // TODO: Validate and persist authenticated user's settings.
        return new UserSettingResponse(null, request.language(), request.theme(), request.notificationEnabled(), request.privacyProfile(), request.allowMessages(), request.allowFollowRequest(), request.biometricEnabled());
    }
}
