package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import kh.edu.acleda.campuslink.modules.community.enums.EventStatus;
import kh.edu.acleda.campuslink.shared.enums.Language;

/**
 * @param paid  the Free/Paid toggle from the Create Event flow
 * @param price required when paid; settled against the demo wallet, never real money
 */
public record EventCreateRequest(
    @NotBlank @Size(max = 180) String title,
    String description,
    String category,
    String coverImageUrl,
    @NotNull LocalDateTime dateTime,
    String locationName,
    Integer attendeeLimit,
    EventStatus status,
    Language language,
    Boolean paid,
    @Positive BigDecimal price
) {
}
