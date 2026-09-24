package kh.edu.acleda.campuslink.modules.community.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

/** Rename or update group profile (avatar, name). Admins/owner only. */
public record RenameGroupRequest(
    @Size(max = 120) String name,
    String avatarUrl
) {
    public RenameGroupRequest(String name) {
        this(name, null);
    }

    @JsonCreator
    public RenameGroupRequest(
        @JsonProperty("name") String name,
        @JsonProperty("avatarUrl") String avatarUrl
    ) {
        this.name = name;
        this.avatarUrl = avatarUrl;
    }
}
