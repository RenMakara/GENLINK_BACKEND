package kh.edu.acleda.campuslink.modules.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

/**
 * @param currency USD or KHR; defaults to USD. Deposits inherit the goal's currency, and a
 *                 wallet-funded deposit must be USD until the dual-currency wallet ships.
 */
public record SavingGoalCreateRequest(
    @NotBlank @Size(max = 160) String title,
    @NotNull @Positive BigDecimal targetAmount,
    LocalDate targetDate,
    Currency currency
) {
}
