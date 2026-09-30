package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.NotNull;
import kh.edu.acleda.campuslink.modules.community.enums.ChatRole;

/** Change a chat member's role. */
public record ChangeChatRoleRequest(
    @NotNull ChatRole role
) {
}
