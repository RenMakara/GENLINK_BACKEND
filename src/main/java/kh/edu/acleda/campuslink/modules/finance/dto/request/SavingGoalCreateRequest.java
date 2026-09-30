package kh.edu.acleda.campuslink.modules.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

/** @param currency USD or KHR; defaults to USD. Deposits inherit the goal's currency, and a */
public record SavingGoalCreateRequest(
    @NotBlank @Size(max = 160) String title,
    @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal targetAmount,
    LocalDate targetDate,
    Currency currency
) {
}
