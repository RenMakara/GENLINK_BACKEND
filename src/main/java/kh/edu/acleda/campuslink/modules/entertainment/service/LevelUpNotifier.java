package kh.edu.acleda.campuslink.modules.entertainment.service;

import kh.edu.acleda.campuslink.modules.user.entity.User;

/** Emits the notification for a newly reached user level. */
public interface LevelUpNotifier {

    void notifyForLevelUp(User user, int level, int totalXp);
}
