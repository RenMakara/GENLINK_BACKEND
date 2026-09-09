package kh.edu.acleda.campuslink.modules.profile.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.enums.Theme;

public record UserSettingUpdateRequest(
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

    /** Backward-compatible constructor for callers that update the original settings fields. */
    public UserSettingUpdateRequest(Language language,
                                    Theme theme,
                                    Boolean notificationEnabled,
                                    String privacyProfile,
                                    Boolean allowMessages,
                                    Boolean allowFollowRequest,
                                    Boolean biometricEnabled) {
        this(language, theme, notificationEnabled, null, null, null, privacyProfile,
            allowMessages, allowFollowRequest, biometricEnabled);
    }
}
