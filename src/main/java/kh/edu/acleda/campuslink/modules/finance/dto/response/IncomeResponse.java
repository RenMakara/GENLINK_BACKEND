package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

public record IncomeResponse(
    UUID incomeId,
    String title,
    BigDecimal amount,
    Currency currency,
    String source,
    String description,
    LocalDate incomeDate,
    Instant createdAt
) {
}
