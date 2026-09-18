package kh.edu.acleda.campuslink.modules.community.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Add people to a group chat. Admins/owner only. */
public record AddMembersRequest(
    @NotEmpty List<@NotNull UUID> userIds
) {
    @JsonCreator
    public AddMembersRequest(
        @JsonProperty("userIds") List<UUID> userIds,
        @JsonProperty("memberIds") List<UUID> memberIds,
        @JsonProperty("memberUserIds") List<UUID> memberUserIds
    ) {
        this((userIds != null && !userIds.isEmpty())
            ? userIds
            : ((memberIds != null && !memberIds.isEmpty())
                ? memberIds
                : (memberUserIds != null ? memberUserIds : List.of())));
    }
}
