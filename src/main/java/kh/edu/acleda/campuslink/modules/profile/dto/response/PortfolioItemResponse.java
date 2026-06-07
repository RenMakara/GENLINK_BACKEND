package kh.edu.acleda.campuslink.modules.profile.dto.response;

import java.util.UUID;

public record PortfolioItemResponse(
    UUID portfolioId,
    String title,
    String description,
    String link,
    String imageUrl
) {
}
