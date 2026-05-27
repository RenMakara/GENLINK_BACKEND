package kh.edu.acleda.campuslink.modules.user.dto;

import kh.edu.acleda.campuslink.modules.user.entity.AuthProvider;
import kh.edu.acleda.campuslink.modules.user.entity.Role;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String fullName,
    String phone,
    String university,
    String bio,
    String avatarUrl,
    AuthProvider authProvider,
    Role role,
    boolean active
) {}
