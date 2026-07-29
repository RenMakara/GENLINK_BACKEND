package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.NotNull;
import kh.edu.acleda.campuslink.modules.community.enums.CommunityRole;

/**
 * Sets a member's role. OWNER is intentionally not a valid target here - ownership only
 * moves through the transfer-ownership endpoint, which demotes the outgoing owner in the
 * same transaction so a community never has two owners.
 */
public record ChangeMemberRoleRequest(
    @NotNull CommunityRole role
) {
}
