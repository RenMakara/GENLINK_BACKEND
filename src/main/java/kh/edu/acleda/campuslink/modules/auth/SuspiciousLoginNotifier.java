package kh.edu.acleda.campuslink.modules.auth;

import kh.edu.acleda.campuslink.modules.user.entity.User;

/** Emits an in-app alert when a returning user signs in from an unknown login context. */
public interface SuspiciousLoginNotifier {

    void notifyIfNeeded(User user, String ipAddress, String userAgent);
}
