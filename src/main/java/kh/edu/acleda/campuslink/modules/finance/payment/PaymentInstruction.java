package kh.edu.acleda.campuslink.modules.finance.payment;

import java.math.BigDecimal;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

/**
 * What the wallet hands a {@link PaymentProvider} to authorize a charge. It carries only the facts
 * a provider needs to decide - never a wallet, a ledger row, or any balance. The provider treats
 * this as read-only intent; the actual money movement is the ledger's job afterwards.
 *
 * @param amount   how much to charge
 * @param currency the currency of {@code amount}
 * @param merchant who is being paid (display only)
 * @param note     optional free-text reference from the payer
 */
public record PaymentInstruction(
    BigDecimal amount,
    Currency currency,
    String merchant,
    String note
) {
}
