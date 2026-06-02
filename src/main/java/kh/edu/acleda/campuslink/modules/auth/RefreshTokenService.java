package kh.edu.acleda.campuslink.modules.auth;

import kh.edu.acleda.campuslink.modules.auth.dto.RefreshTokenRotation;
import kh.edu.acleda.campuslink.modules.user.entity.User;

public interface RefreshTokenService {
    String create(User user);
    String create(User user, String deviceInfo, String ipAddress);
    RefreshTokenRotation rotate(String token);
    RefreshTokenRotation rotate(String token, String deviceInfo, String ipAddress);
    void revoke(User user);
    void revoke(String token);
}
