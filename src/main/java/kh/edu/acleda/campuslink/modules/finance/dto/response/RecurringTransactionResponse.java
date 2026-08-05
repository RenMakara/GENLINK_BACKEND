package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.RecurringFrequency;
import kh.edu.acleda.campuslink.modules.finance.enums.RecurringType;

public record RecurringTransactionResponse(
    UUID id,
    RecurringType type,
    String title,
    BigDecimal amount,
    Currency currency,
    String category,
    RecurringFrequency frequency,
    boolean fromWallet,
    LocalDate nextRunDate,
    LocalDate startDate,
    LocalDate endDate,
    LocalDate lastRunDate,
    boolean active,
    Instant createdAt
) {
}
