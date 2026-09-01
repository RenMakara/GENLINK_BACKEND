package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

public record ExpenseResponse(
    UUID expenseId,
    String title,
    BigDecimal amount,
    Currency currency,
    String category,
    String description,
    LocalDate expenseDate,
    String paymentMethod,
    boolean paidFromWallet,
    Instant createdAt
) {
}
