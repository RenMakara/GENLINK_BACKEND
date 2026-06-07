package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.NotBlank;
import kh.edu.acleda.campuslink.modules.community.enums.PostType;
import kh.edu.acleda.campuslink.modules.community.enums.Visibility;
import kh.edu.acleda.campuslink.shared.enums.Language;

public record PostCreateRequest(
    @NotBlank String contentText,
    String imageUrl,
    PostType postType,
    Visibility visibility,
    Language language
) {
}
