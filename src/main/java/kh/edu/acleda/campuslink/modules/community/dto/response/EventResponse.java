package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.EventStatus;
import kh.edu.acleda.campuslink.modules.community.enums.RsvpStatus;
import kh.edu.acleda.campuslink.shared.enums.Language;

/**
 * @param myRsvpStatus null when the caller has not responded; drives Join vs Cancel
 *                     without a request per row
 * @param full         attendeeLimit reached, so the client can disable Join up front
 * @param price        simulation only - paid RSVP settles against the demo wallet
 */
public record EventResponse(
    UUID eventId,
    PostResponse.AuthorSummary organizer,
    String title,
    String description,
    String category,
    String coverImageUrl,
    LocalDateTime dateTime,
    String locationName,
    Integer attendeeLimit,
    Integer attendeeCount,
    EventStatus status,
    Language language,
    boolean paid,
    BigDecimal price,
    RsvpStatus myRsvpStatus,
    boolean full,
    Instant createdAt
) {
}
