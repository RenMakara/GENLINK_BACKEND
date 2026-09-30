package kh.edu.acleda.campuslink.modules.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.RecurringFrequency;
import kh.edu.acleda.campuslink.modules.finance.enums.RecurringType;

/** Defines a recurring income or expense. */
public record RecurringTransactionCreateRequest(
    @NotNull RecurringType type,
    @NotBlank @Size(max = 160) String title,
    @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
    Currency currency,
    @Size(max = 80) String category,
    @NotNull RecurringFrequency frequency,
    Boolean fromWallet,
    @NotNull LocalDate startDate,
    LocalDate endDate
) {
}
