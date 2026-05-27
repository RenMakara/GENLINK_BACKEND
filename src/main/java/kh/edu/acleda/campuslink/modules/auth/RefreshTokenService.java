package kh.edu.acleda.campuslink.modules.auth;

import kh.edu.acleda.campuslink.modules.auth.entity.RefreshToken;
import kh.edu.acleda.campuslink.modules.user.entity.User;

public interface RefreshTokenService {
    String create(User user);
    RefreshToken rotate(String token);
    void revoke(User user);
}
