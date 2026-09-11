package kh.edu.acleda.campuslink.modules.notification.service;

import java.util.List;
import kh.edu.acleda.campuslink.modules.notification.dto.request.NotificationPreferenceUpdateRequest;
import kh.edu.acleda.campuslink.modules.notification.dto.response.NotificationPreferenceResponse;

public interface NotificationPreferenceService {

    List<NotificationPreferenceResponse> getMine();

    NotificationPreferenceResponse update(
        String notificationType, NotificationPreferenceUpdateRequest request);
}
