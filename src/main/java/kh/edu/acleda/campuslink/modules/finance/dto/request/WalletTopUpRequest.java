package kh.edu.acleda.campuslink.modules.finance.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

/**
 * Simulated deposit into the demo wallet. No real money moves.
 *
 * @param currency which balance to top up (USD or KHR); defaults to USD when omitted.
 */
public record WalletTopUpRequest(
    @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
    @Size(max = 200) String note,
    Currency currency
) {
}
