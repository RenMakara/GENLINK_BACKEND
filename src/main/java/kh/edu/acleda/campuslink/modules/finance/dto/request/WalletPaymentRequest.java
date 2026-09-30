package kh.edu.acleda.campuslink.modules.finance.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

/** A simulated KHQR payment. */
public record WalletPaymentRequest(
    @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
    @Size(max = 160) String merchant,
    @Size(max = 200) String note,
    Currency currency
) {
}
