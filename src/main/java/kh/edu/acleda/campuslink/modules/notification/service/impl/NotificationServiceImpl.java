package kh.edu.acleda.campuslink.modules.notification.service.impl;

import kh.edu.acleda.campuslink.modules.notification.dto.response.NotificationResponse;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationServiceImpl implements NotificationService {

    @Override
    public List<NotificationResponse> getMine() {
        // TODO: Load notifications for authenticated user and localize template output if needed.
        return List.of();
    }

    @Override
    public void markRead(UUID notificationId) {
        // TODO: Mark notification as read after ownership validation.
    }
}
