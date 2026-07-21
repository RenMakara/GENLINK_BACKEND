package kh.edu.acleda.campuslink.modules.entertainment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Metadata only - no media upload. videoUrl/thumbnailUrl point at already-hosted media. */
public record ShortContentCreateRequest(
    @NotBlank @Size(max = 180) String title,
    String description,
    @Size(max = 80) String category,
    String videoUrl,
    String thumbnailUrl
) {
}
