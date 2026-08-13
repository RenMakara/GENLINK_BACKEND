package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.NotNull;
import kh.edu.acleda.campuslink.modules.community.enums.ChatRole;

/**
 * Change a chat member's role. Owner only. Only ADMIN or MEMBER are valid targets — OWNER is set
 * at creation and ownership transfer is not part of this phase.
 */
public record ChangeChatRoleRequest(
    @NotNull ChatRole role
) {
}
