package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.EventMode;
import kh.edu.acleda.campuslink.modules.community.enums.EventStatus;
import kh.edu.acleda.campuslink.modules.community.enums.RsvpStatus;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.shared.enums.Language;

/** @param myRsvpStatus null when the caller has not responded; drives Join vs Cancel */
public record EventResponse(
    UUID eventId,
    PostResponse.AuthorSummary organizer,
    String title,
    String description,
    String category,
    String coverImageUrl,
    LocalDateTime startDateTime,
    LocalDateTime endDateTime,
    EventMode mode,
    String meetingUrl,
    String locationName,
    BigDecimal latitude,
    BigDecimal longitude,
    Integer attendeeLimit,
    Integer attendeeCount,
    EventStatus status,
    Language language,
    boolean paid,
    BigDecimal price,
    Currency currency,
    RsvpStatus myRsvpStatus,
    boolean full,
    Instant createdAt
) {
}
