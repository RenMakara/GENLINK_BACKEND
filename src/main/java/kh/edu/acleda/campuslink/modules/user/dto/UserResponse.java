package kh.edu.acleda.campuslink.modules.user.dto;

import kh.edu.acleda.campuslink.modules.user.entity.Role;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String fullName,
    Role role,
    boolean active
) {}
