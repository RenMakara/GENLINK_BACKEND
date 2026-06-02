package kh.edu.acleda.campuslink.modules.user.dto;

import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.Role;
import kh.edu.acleda.campuslink.modules.user.entity.WorkStatus;
import java.util.UUID;

public record UserResponse(
    UUID userId,
    String fullName,
    String username,
    String email,
    String phoneNumber,
    String avatarUrl,
    String bio,
    String universityName,
    WorkStatus workStatus,
    String location,
    boolean emailVerified,
    AccountStatus accountStatus,
    int totalXp,
    int level,
    Role role,
    boolean active
) {}
