package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kh.edu.acleda.campuslink.modules.community.enums.CommunityPrivacy;

/** The slug is derived from the name, never supplied by the client. */
public record CommunityCreateRequest(
    @NotBlank @Size(max = 120) String name,
    String description,
    @Size(max = 60) String category,
    String coverImageUrl,
    String avatarUrl,
    CommunityPrivacy privacy
) {
}
