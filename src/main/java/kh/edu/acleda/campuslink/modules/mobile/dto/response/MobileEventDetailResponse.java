package kh.edu.acleda.campuslink.modules.mobile.dto.response;

import java.util.UUID;

public record MobileEventDetailResponse(
    UUID eventId,
    String message
) {
}
