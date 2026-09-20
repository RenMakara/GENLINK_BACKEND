package kh.edu.acleda.campuslink.modules.entertainment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Metadata for a short content item. Media is uploaded through the shared media
 * pipeline first; media IDs are preferred so the service can resolve an owned,
 * confirmed public URL instead of trusting an arbitrary client URL.
 */
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
