package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.enums.SavingGoalStatus;

/**
 * @param remainingAmount targetAmount - currentAmount, floored at zero (an over-funded
 *                        goal shows 0 remaining, never a negative).
 * @param progressPercent currentAmount / targetAmount as a whole percent, capped at 100
 *                        - ready for a progress bar.
 */
public record SavingGoalResponse(
    UUID savingGoalId,
    String title,
    BigDecimal targetAmount,
    BigDecimal currentAmount,
    BigDecimal remainingAmount,
    int progressPercent,
    LocalDate targetDate,
    SavingGoalStatus status
) {
}
