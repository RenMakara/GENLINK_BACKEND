package kh.edu.acleda.campuslink.modules.entertainment.service.impl;

import java.util.Map;
import kh.edu.acleda.campuslink.modules.entertainment.entity.Badge;
import kh.edu.acleda.campuslink.modules.entertainment.service.BadgeAwardNotifier;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Builds the navigable notification for a newly unlocked badge. */
@Slf4j
@Service
public class BadgeAwardNotifierImpl implements BadgeAwardNotifier {

    static final String TEMPLATE_KEY = "ENTERTAINMENT_BADGE_AWARDED";
    static final String NOTIFICATION_TYPE = "ENTERTAINMENT_BADGE";

    private final NotificationEmitter notificationEmitter;

    public BadgeAwardNotifierImpl(NotificationEmitter notificationEmitter) {
        this.notificationEmitter = notificationEmitter;
    }

    @Override
    public void notifyForAwardedBadge(User user, Badge badge) {
        if (user == null || user.getId() == null || badge == null || badge.getId() == null) {
            log.warn("Skipping badge notification because badge details are incomplete: user={} badge={}",
                user == null ? null : user.getId(), badge == null ? null : badge.getId());
            return;
        }

        String badgeName = localizedBadgeName(user, badge);
        if (badgeName == null || badgeName.isBlank()) {
            log.warn("Skipping badge notification because badge name is empty: badge={}", badge.getId());
            return;
        }

        notificationEmitter.emit(new NotificationRequest(
            user.getId(),
            TEMPLATE_KEY,
            NOTIFICATION_TYPE,
            Map.of("badgeName", badgeName),
            "BADGE",
            badge.getId(),
            "badge-awarded:" + badge.getId()));
    }

    private String localizedBadgeName(User user, Badge badge) {
        if (user.getPreferredLanguage() == Language.KHMER
            && badge.getNameKm() != null && !badge.getNameKm().isBlank()) {
            return badge.getNameKm();
        }
        return badge.getNameEn();
    }
}
