package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.NotBlank;
import kh.edu.acleda.campuslink.modules.community.enums.PostType;
import kh.edu.acleda.campuslink.modules.community.enums.Visibility;
import kh.edu.acleda.campuslink.shared.enums.Language;

/**
 * @param communityId optional. Set it to post into a community; required in practice
 *                    for visibility = COMMUNITY, which is otherwise visible to nobody.
 */
public record PostCreateRequest(
    @NotBlank String contentText,
    String imageUrl,
    PostType postType,
    Visibility visibility,
    Language language,
    java.util.UUID communityId
) {
}
