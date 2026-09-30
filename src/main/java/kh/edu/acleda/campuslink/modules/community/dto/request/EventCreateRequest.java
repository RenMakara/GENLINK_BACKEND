package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import kh.edu.acleda.campuslink.modules.community.enums.EventStatus;
import kh.edu.acleda.campuslink.modules.community.enums.EventMode;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.shared.enums.Language;

/** Event creation/update payload shared by the organizer flow and the API. */
public record EventCreateRequest(
    @NotBlank @Size(max = 180) String title,
    String description,
    String category,
    String coverImageUrl,
    @NotNull LocalDateTime startDateTime,
    @NotNull LocalDateTime endDateTime,
    @NotNull EventMode mode,
    @Size(max = 2048) String meetingUrl,
    String locationName,
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0") BigDecimal latitude,
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0") BigDecimal longitude,
    @Positive Integer attendeeLimit,
    EventStatus status,
    Language language,
    Boolean paid,
    @Positive BigDecimal price,
    Currency currency
) {

    /** Source-compatible constructor for callers written before event currency was added. */
    public EventCreateRequest(String title, String description, String category,
                              String coverImageUrl, LocalDateTime startDateTime,
                              LocalDateTime endDateTime, EventMode mode, String meetingUrl,
                              String locationName, BigDecimal latitude, BigDecimal longitude,
                              Integer attendeeLimit, EventStatus status, Language language,
                              Boolean paid, BigDecimal price) {
        this(title, description, category, coverImageUrl, startDateTime, endDateTime, mode,
            meetingUrl, locationName, latitude, longitude, attendeeLimit, status, language,
            paid, price, Boolean.TRUE.equals(paid) ? Currency.USD : null);
    }

    /** Source-compatible constructor for older backend callers. */
    public EventCreateRequest(String title, String description, String category,
                              String coverImageUrl, LocalDateTime dateTime,
                              String locationName, Integer attendeeLimit,
                              EventStatus status, Language language, Boolean paid,
                              BigDecimal price) {
        this(title, description, category, coverImageUrl, dateTime,
            dateTime == null ? null : dateTime.plusHours(1), null, null,
            locationName, null, null, attendeeLimit, status, language, paid, price,
            Boolean.TRUE.equals(paid) ? Currency.USD : null);
    }
}
