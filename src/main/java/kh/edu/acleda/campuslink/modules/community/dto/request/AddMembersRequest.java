package kh.edu.acleda.campuslink.modules.community.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Add people to a group chat. Admins/owner only. */
public record AddMembersRequest(
    @JsonProperty("userIds") @JsonAlias({"memberIds", "memberUserIds", "userIds"}) @NotEmpty List<@NotNull UUID> userIds
) {
}
