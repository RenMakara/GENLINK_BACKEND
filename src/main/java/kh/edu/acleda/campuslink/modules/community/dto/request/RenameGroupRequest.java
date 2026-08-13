package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Rename a group chat. Admins/owner only. */
public record RenameGroupRequest(
    @NotBlank @Size(max = 120) String name
) {
}
