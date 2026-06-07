package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.enums.SavingGoalStatus;

public record SavingGoalResponse(
    UUID savingGoalId,
    String title,
    BigDecimal targetAmount,
    BigDecimal currentAmount,
    LocalDate targetDate,
    SavingGoalStatus status
) {
}
