package kh.edu.acleda.campuslink.modules.finance.payment;

import java.math.BigDecimal;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

/** What the wallet hands a {@link PaymentProvider} to authorize a charge. */
public record PaymentInstruction(
    BigDecimal amount,
    Currency currency,
    String merchant,
    String note
) {
}
