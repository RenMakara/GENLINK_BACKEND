package kh.edu.acleda.campuslink.modules.notification.service;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.dto.request.NotificationDeviceRegistrationRequest;
import kh.edu.acleda.campuslink.modules.notification.dto.response.NotificationDeviceResponse;

public interface NotificationDeviceService {

    List<NotificationDeviceResponse> getMine();

    NotificationDeviceResponse register(NotificationDeviceRegistrationRequest request);

    void deactivate(UUID deviceId);
}
