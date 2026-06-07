package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kh.edu.acleda.campuslink.shared.enums.Language;

public record DiscussionCreateRequest(
    @NotBlank @Size(max = 180) String title,
    @NotBlank String content,
    String category,
    Boolean anonymous,
    Language language
) {
}
