package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.math.BigDecimal;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.RsvpStatus;

/**
 * @param amountPaid   null for a free event. When set, it came out of the demo wallet.
 * @param simulatedPayment always true when amountPaid is set - there is no real
 *                         payment rail, and the client must say so on screen
 */
public record RsvpResponse(
    UUID eventId,
    RsvpStatus status,
    Integer attendeeCount,
    Integer xpAwarded,
    BigDecimal amountPaid,
    boolean simulatedPayment
) {
}
