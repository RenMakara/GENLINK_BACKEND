package kh.edu.acleda.campuslink.modules.entertainment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Metadata for a short content item. */
public record ShortContentCreateRequest(
    @NotBlank @Size(max = 180) String title,
    String description,
    @Size(max = 80) String category,
    String videoUrl,
    String thumbnailUrl,
    UUID videoMediaId,
    UUID thumbnailMediaId
) {
}
