package kh.edu.acleda.campuslink.modules.entertainment.service.impl;

import java.util.Map;
import kh.edu.acleda.campuslink.modules.entertainment.service.LevelUpNotifier;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Builds the navigable notification for a newly reached level. */
@Slf4j
@Service
public class LevelUpNotifierImpl implements LevelUpNotifier {

    static final String TEMPLATE_KEY = "ENTERTAINMENT_LEVEL_UP";
    static final String NOTIFICATION_TYPE = "ENTERTAINMENT_LEVEL_UP";

    private final NotificationEmitter notificationEmitter;

    public LevelUpNotifierImpl(NotificationEmitter notificationEmitter) {
        this.notificationEmitter = notificationEmitter;
    }

    @Override
    public void notifyForLevelUp(User user, int level, int totalXp) {
        if (user == null || user.getId() == null || level <= 0 || totalXp < 0) {
            log.warn("Skipping level-up notification because details are incomplete: user={} level={} totalXp={}",
                user == null ? null : user.getId(), level, totalXp);
            return;
        }

        notificationEmitter.emit(new NotificationRequest(
            user.getId(),
            TEMPLATE_KEY,
            NOTIFICATION_TYPE,
            Map.of(
                "level", Integer.toString(level),
                "totalXp", Integer.toString(totalXp)),
            "ACHIEVEMENT",
            user.getId(),
            "level-up:" + user.getId() + ":" + level));
    }
}
