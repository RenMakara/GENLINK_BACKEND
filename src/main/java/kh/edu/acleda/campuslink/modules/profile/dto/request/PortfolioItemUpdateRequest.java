package kh.edu.acleda.campuslink.modules.profile.dto.request;

import jakarta.validation.constraints.Size;

public record PortfolioItemUpdateRequest(
    @Size(max = 160) String title,
    String description,
    String link,
    String imageUrl
) {
}
