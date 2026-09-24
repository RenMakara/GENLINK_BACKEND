package kh.edu.acleda.campuslink.modules.community.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Create a group chat. The caller is added automatically as the creator (implicit admin for the
 * MVP), so {@code memberIds} is the other people to include.
 */
public record CreateGroupRequest(
    @NotBlank @Size(max = 120) String name,
    @NotEmpty List<@NotNull UUID> memberIds,
    String avatarUrl
) {
    public CreateGroupRequest(String name, List<UUID> memberIds) {
        this(name, memberIds, null);
    }

    @JsonCreator
    public CreateGroupRequest(
        @JsonProperty("name") String name,
        @JsonProperty("memberIds") List<UUID> memberIds,
        @JsonProperty("memberUserIds") List<UUID> memberUserIds,
        @JsonProperty("avatarUrl") String avatarUrl
    ) {
        this(name, (memberIds != null && !memberIds.isEmpty())
            ? memberIds
            : (memberUserIds != null ? memberUserIds : List.of()),
            avatarUrl);
    }
}
