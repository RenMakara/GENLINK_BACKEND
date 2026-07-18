package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseResponse(
    UUID expenseId,
    String title,
    BigDecimal amount,
    String category,
    String description,
    LocalDate expenseDate,
    String paymentMethod,
    Instant createdAt
) {
}
