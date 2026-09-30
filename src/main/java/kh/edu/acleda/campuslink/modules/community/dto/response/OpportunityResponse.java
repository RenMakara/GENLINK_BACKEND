package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** @param savedByMe resolved server-side so a list does not fire a request per row to */
public record OpportunityResponse(
    UUID opportunityId,
    String title,
    String organization,
    String type,
    String description,
    LocalDate deadline,
    String location,
    String applyLink,
    boolean savedByMe,
    boolean expired,
    Instant createdAt
) {
}
