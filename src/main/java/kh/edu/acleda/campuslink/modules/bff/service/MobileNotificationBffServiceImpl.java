package kh.edu.acleda.campuslink.modules.bff.service;

import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileNotificationsResponse;
import kh.edu.acleda.campuslink.modules.notification.dto.response.NotificationResponse;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationService;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the mobile notification snapshot through the Notification application port. */
@Service
public class MobileNotificationBffServiceImpl implements MobileNotificationBffService {

    static final int MAX_LIMIT = 20;

    private final NotificationService notificationService;

    public MobileNotificationBffServiceImpl(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(readOnly = true)
    public MobileNotificationsResponse getNotifications(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ApiException(
                "Notification limit must be between 1 and " + MAX_LIMIT,
                "VALIDATION_ERROR");
        }

        PageResponse<NotificationResponse> recent = notificationService.getMine(
            false, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new MobileNotificationsResponse(
            notificationService.unreadCount(),
            recent.content(),
            recent.hasNext());
    }
}
