package kh.edu.acleda.campuslink.modules.profile.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kh.edu.acleda.campuslink.modules.profile.enums.SocialPlatform;

public record SocialLinkUpsertRequest(
    @NotNull SocialPlatform platform,
    @NotBlank @Size(max = 500) String url
) {
}
