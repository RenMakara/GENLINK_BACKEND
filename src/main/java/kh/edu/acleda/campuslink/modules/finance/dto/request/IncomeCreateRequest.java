package kh.edu.acleda.campuslink.modules.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

/**
 * @param currency USD or KHR; defaults to USD when omitted. Only USD income credits the demo
 *                 wallet until the dual-currency wallet ships (Phase 3b); KHR income is tracked.
 */
public record IncomeCreateRequest(
    @NotBlank @Size(max = 160) String title,
    @NotNull @Positive BigDecimal amount,
    String source,
    String description,
    @NotNull LocalDate incomeDate,
    Currency currency
) {
}
