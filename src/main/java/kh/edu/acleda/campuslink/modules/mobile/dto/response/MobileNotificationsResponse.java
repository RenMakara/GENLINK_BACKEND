package kh.edu.acleda.campuslink.modules.mobile.dto.response;

import java.util.List;
import kh.edu.acleda.campuslink.modules.notification.dto.response.NotificationResponse;

/** Compact notification snapshot for the mobile home/bell surface. */
public record MobileNotificationsResponse(
    long unreadCount,
    List<NotificationResponse> recentNotifications,
    boolean hasMore
) {
}
