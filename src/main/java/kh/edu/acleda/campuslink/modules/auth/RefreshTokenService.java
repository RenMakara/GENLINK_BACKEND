package kh.edu.acleda.campuslink.modules.auth;

import kh.edu.acleda.campuslink.modules.auth.dto.RefreshTokenRotation;
import kh.edu.acleda.campuslink.modules.user.entity.User;

public interface RefreshTokenService {
    String create(User user);
    String create(User user, String deviceInfo, String ipAddress);
    String create(User user, String deviceInfo, String ipAddress, String userAgent);
    /**
     * Creates a refresh token with an explicit lifetime.
     *
     * <p>Exists for the admin portal, whose sessions last 8 hours rather than the mobile app's
     * 7 days (SSOT ADM-023). Adding the parameter here keeps one implementation of hashing,
     * storage and revocation - a second copy in the admin module would be the thing that
     * quietly stops matching when this one is fixed.
     */
    String create(User user, String deviceInfo, String ipAddress, String userAgent, long ttlMs);

    RefreshTokenRotation rotate(String token);
    RefreshTokenRotation rotate(String token, String deviceInfo, String ipAddress);
    RefreshTokenRotation rotate(String token, String deviceInfo, String ipAddress, String userAgent);

    /** Rotation with an explicit lifetime for the replacement - the admin portal's 8 hours. */
    RefreshTokenRotation rotate(String token, String deviceInfo, String ipAddress, String userAgent, long ttlMs);
    void revoke(User user);
    void revoke(String token);
}
