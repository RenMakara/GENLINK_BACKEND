package kh.edu.acleda.campuslink.modules.finance.enums;

public enum TransactionType {
    /** A KHQR payment. Provider-neutral: which network processed it lives in the ledger's
     *  {@code provider}/{@code provider_ref} columns, not in this type. */
    KHQR_PAYMENT,
    EVENT_PAYMENT,
    DEPOSIT,
    /** Simulated learning-budget credit for passing a financial-literacy quiz. */
    REWARD,
    /** A logged spend (Phase 2 wires expenses to emit one). */
    EXPENSE,
    /** Money in - allowance, part-time, scholarship (Phase 1). */
    INCOME,
    /** Money moved into a saving goal (Phase 2). */
    SAVING
}
