package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.math.BigDecimal;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.RsvpStatus;

/** @param amountPaid null for a free event. When set, it came out of the demo wallet. */
public record RsvpResponse(
    UUID eventId,
    RsvpStatus status,
    Integer attendeeCount,
    Integer xpAwarded,
    BigDecimal amountPaid,
    boolean simulatedPayment
) {
}
