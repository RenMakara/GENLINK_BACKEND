package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.EventStatus;
import kh.edu.acleda.campuslink.shared.enums.Language;

public record EventResponse(
    UUID eventId,
    String title,
    String description,
    String category,
    LocalDateTime dateTime,
    String locationName,
    Integer attendeeCount,
    EventStatus status,
    Language language
) {
}
