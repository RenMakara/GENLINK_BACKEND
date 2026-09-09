package kh.edu.acleda.campuslink.modules.bff.service;

import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileNotificationsResponse;

/** BFF read model for the mobile notification bell. */
public interface MobileNotificationBffService {

    /** Returns the unread badge and a bounded newest-first preview for the current user. */
    MobileNotificationsResponse getNotifications(int limit);
}
