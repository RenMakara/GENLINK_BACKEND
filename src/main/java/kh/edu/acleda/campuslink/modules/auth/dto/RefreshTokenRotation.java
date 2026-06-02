package kh.edu.acleda.campuslink.modules.auth.dto;

import kh.edu.acleda.campuslink.modules.user.entity.User;

public record RefreshTokenRotation(User user, String refreshToken) {
}
