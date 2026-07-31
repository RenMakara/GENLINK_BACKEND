package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.enums.AutoSaveType;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

public record AutoSaveRuleResponse(
    UUID id,
    UUID savingGoalId,
    String goalTitle,
    AutoSaveType type,
    BigDecimal percent,
    Currency currency,
    LocalDate nextRunDate,
    LocalDate lastRunDate,
    boolean active,
    Instant createdAt
) {
}
