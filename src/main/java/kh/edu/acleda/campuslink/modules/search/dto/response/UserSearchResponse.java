package kh.edu.acleda.campuslink.modules.search.dto.response;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.modules.user.entity.WorkStatus;

public record UserSearchResponse(
    UUID userId,
    String username,
    String fullName,
    String avatarUrl,
    String universityName,
    String bio,
    WorkStatus workStatus,
    int level,
    int totalXp
) {
    public static UserSearchResponse from(User user) {
        return new UserSearchResponse(
            user.getId(),
            user.getUsername(),
            user.getFullName(),
            user.getAvatarUrl(),
            user.getUniversityName(),
            user.getBio(),
            user.getWorkStatus(),
            user.getLevel(),
            user.getTotalXp()
        );
    }
}
