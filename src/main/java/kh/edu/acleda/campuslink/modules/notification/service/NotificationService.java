package kh.edu.acleda.campuslink.modules.notification.service;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.dto.response.NotificationResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

/** The current user's in-app notifications: read them, mark them read. */
public interface NotificationService {

    /** The user's notifications, newest first; {@code unreadOnly} narrows to unread. */
    PageResponse<NotificationResponse> getMine(boolean unreadOnly, Pageable pageable);

    /** How many the user hasn't read yet (bell badge). */
    long unreadCount();

    /** Mark one as read. Idempotent; another user's id resolves to not-found. */
    void markRead(UUID notificationId);

    /** Mark all of the user's unread notifications as read; returns how many were changed. */
    int markAllRead();
}
