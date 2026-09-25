package kh.edu.acleda.campuslink.modules.admin.analytics.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Validated request for one synchronous analytics CSV export. */
public record AdminAnalyticsExportRequest(
    @NotBlank String series,
    LocalDate from,
    LocalDate to,
    String granularity,
    @NotBlank @Size(min = 10, max = 2_000) String reason
) {
}
