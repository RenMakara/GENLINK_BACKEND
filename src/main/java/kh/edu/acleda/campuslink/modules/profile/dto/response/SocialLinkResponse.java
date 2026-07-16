package kh.edu.acleda.campuslink.modules.profile.dto.response;

import kh.edu.acleda.campuslink.modules.profile.enums.SocialPlatform;

public record SocialLinkResponse(
    SocialPlatform platform,
    String url
) {
}
