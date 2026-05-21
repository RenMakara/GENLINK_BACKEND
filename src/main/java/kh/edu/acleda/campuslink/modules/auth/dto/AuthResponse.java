package kh.edu.acleda.campuslink.modules.auth.dto;

import kh.edu.acleda.campuslink.modules.user.dto.UserResponse;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    UserResponse user
) {}
