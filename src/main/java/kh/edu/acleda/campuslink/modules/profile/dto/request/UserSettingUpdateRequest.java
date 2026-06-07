package kh.edu.acleda.campuslink.modules.profile.dto.request;

import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.enums.Theme;

public record UserSettingUpdateRequest(
    Language language,
    Theme theme,
    Boolean notificationEnabled,
    String privacyProfile,
    Boolean allowMessages,
    Boolean allowFollowRequest,
    Boolean biometricEnabled
) {
}
