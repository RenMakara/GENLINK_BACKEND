package kh.edu.acleda.campuslink.modules.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.RecurringFrequency;
import kh.edu.acleda.campuslink.modules.finance.enums.RecurringType;

/**
 * Defines a recurring income or expense. It first materialises on {@code startDate} and repeats by
 * {@code frequency} until {@code endDate} (or forever when that is null).
 *
 * @param currency    USD or KHR; defaults to USD when omitted.
 * @param category    expense category or income source (free text).
 * @param fromWallet  EXPENSE only: also debit the wallet on each run (income always credits).
 */
public record RecurringTransactionCreateRequest(
    @NotNull RecurringType type,
    @NotBlank @Size(max = 160) String title,
    @NotNull @Positive BigDecimal amount,
    Currency currency,
    @Size(max = 80) String category,
    @NotNull RecurringFrequency frequency,
    Boolean fromWallet,
    @NotNull LocalDate startDate,
    LocalDate endDate
) {
}
