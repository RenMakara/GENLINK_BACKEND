package kh.edu.acleda.campuslink.modules.finance.enums;

/**
 * Which way money moved on a ledger row. Kept separate from {@link TransactionType} so a
 * balance or cash-flow figure is a simple sum over the direction, independent of the many
 * reasons (types) a transaction can have.
 */
public enum TransactionDirection {
    /** Money into the wallet: top-up, reward, income. */
    CREDIT,
    /** Money out of the wallet: payment, expense, saving transfer. */
    DEBIT
}
