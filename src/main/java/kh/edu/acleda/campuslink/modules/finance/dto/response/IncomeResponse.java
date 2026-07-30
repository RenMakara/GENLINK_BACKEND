package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record IncomeResponse(
    UUID incomeId,
    String title,
    BigDecimal amount,
    String source,
    String description,
    LocalDate incomeDate,
    Instant createdAt
) {
}
