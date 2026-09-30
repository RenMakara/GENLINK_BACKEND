package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import kh.edu.acleda.campuslink.shared.enums.Language;

/** @param parentCommentId null for a top-level comment; set it to reply. Threading is */
public record CommentCreateRequest(
    @NotBlank @Size(max = 2000) String content,
    UUID parentCommentId,
    Language language
) {
}
