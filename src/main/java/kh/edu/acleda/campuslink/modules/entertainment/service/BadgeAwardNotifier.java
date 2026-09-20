package kh.edu.acleda.campuslink.modules.entertainment.service;

import kh.edu.acleda.campuslink.modules.entertainment.entity.Badge;
import kh.edu.acleda.campuslink.modules.user.entity.User;

/** Emits the notification for a badge that was newly unlocked. */
public interface BadgeAwardNotifier {

    void notifyForAwardedBadge(User user, Badge badge);
}
