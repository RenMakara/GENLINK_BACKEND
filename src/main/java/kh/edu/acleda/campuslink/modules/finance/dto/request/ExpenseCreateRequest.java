package kh.edu.acleda.campuslink.modules.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

/**
 * @param paidFromWallet when true, the expense also debits the demo wallet (and is rejected if
 *                       funds are too low). Default/false = tracking only, the balance is untouched.
 * @param currency       USD or KHR; defaults to USD when omitted. Wallet-paid expenses must be USD
 *                       until the dual-currency wallet ships (Phase 3b).
 */
public record ExpenseCreateRequest(
    @NotBlank @Size(max = 160) String title,
    @NotNull @Positive BigDecimal amount,
    String category,
    String description,
    @NotNull LocalDate expenseDate,
    String paymentMethod,
    Boolean paidFromWallet,
    Currency currency
) {
}
