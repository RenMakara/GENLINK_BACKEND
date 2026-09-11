package kh.edu.acleda.campuslink.modules.notification.service;

import java.util.List;
import kh.edu.acleda.campuslink.modules.notification.dto.request.NotificationPreferenceUpdateRequest;
import kh.edu.acleda.campuslink.modules.notification.dto.response.NotificationChannelPreferenceResponse;

/** Authenticated profile-settings port for delivery-channel overrides. */
public interface NotificationChannelPreferenceService {

    List<NotificationChannelPreferenceResponse> getMine();

    NotificationChannelPreferenceResponse update(
        String channel, NotificationPreferenceUpdateRequest request);
}
