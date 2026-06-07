package kh.edu.acleda.campuslink.modules.profile.dto.response;

import java.util.UUID;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.enums.Theme;

public record UserSettingResponse(
    UUID settingsId,
    Language language,
    Theme theme,
    Boolean notificationEnabled,
    String privacyProfile,
    Boolean allowMessages,
    Boolean allowFollowRequest,
    Boolean biometricEnabled
) {
}
