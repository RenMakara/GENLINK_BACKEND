package kh.edu.acleda.campuslink.modules.auth;

import kh.edu.acleda.campuslink.modules.auth.dto.RefreshTokenRotation;
import kh.edu.acleda.campuslink.modules.user.entity.User;

public interface RefreshTokenService {
    String create(User user);
    String create(User user, String deviceInfo, String ipAddress);
    String create(User user, String deviceInfo, String ipAddress, String userAgent);
    /** Creates a refresh token with an explicit lifetime. */
    String create(User user, String deviceInfo, String ipAddress, String userAgent, long ttlMs);

    RefreshTokenRotation rotate(String token);
    RefreshTokenRotation rotate(String token, String deviceInfo, String ipAddress);
    RefreshTokenRotation rotate(String token, String deviceInfo, String ipAddress, String userAgent);

    /** Rotation with an explicit lifetime for the replacement - the admin portal's 8 hours. */
    RefreshTokenRotation rotate(String token, String deviceInfo, String ipAddress, String userAgent, long ttlMs);
    void revoke(User user);
    void revoke(String token);
}
