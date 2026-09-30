package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.NotNull;
import kh.edu.acleda.campuslink.modules.community.enums.CommunityRole;

/** Sets a member's role. */
public record ChangeMemberRoleRequest(
    @NotNull CommunityRole role
) {
}
