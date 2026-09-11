package kh.edu.acleda.campuslink.modules.profile.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;
import java.util.UUID;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.enums.Theme;

public record UserSettingResponse(
    UUID settingsId,
    Language language,
    Theme theme,
    Boolean notificationEnabled,
    Boolean quietHoursEnabled,
    @JsonFormat(pattern = "HH:mm")
    LocalTime quietHoursStart,
    @JsonFormat(pattern = "HH:mm")
    LocalTime quietHoursEnd,
    String privacyProfile,
    Boolean allowMessages,
    Boolean allowFollowRequest,
    Boolean biometricEnabled
) {
}
