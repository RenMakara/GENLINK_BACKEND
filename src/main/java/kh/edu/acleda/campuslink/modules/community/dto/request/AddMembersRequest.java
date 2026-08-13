package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Add people to a group chat. Admins/owner only. */
public record AddMembersRequest(
    @NotEmpty List<@NotNull UUID> userIds
) {
}
