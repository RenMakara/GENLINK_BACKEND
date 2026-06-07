package kh.edu.acleda.campuslink.modules.notification.service;

import kh.edu.acleda.campuslink.modules.notification.dto.response.NotificationResponse;

import java.util.List;
import java.util.UUID;

public interface NotificationService {
    List<NotificationResponse> getMine();
    void markRead(UUID notificationId);
}
