package kh.edu.acleda.campuslink.modules.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

/** @param paidFromWallet when true, the expense also debits the demo wallet (and is rejected if */
public record ExpenseCreateRequest(
    @NotBlank @Size(max = 160) String title,
    @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
    String category,
    String description,
    @NotNull LocalDate expenseDate,
    String paymentMethod,
    Boolean paidFromWallet,
    Currency currency
) {
}
