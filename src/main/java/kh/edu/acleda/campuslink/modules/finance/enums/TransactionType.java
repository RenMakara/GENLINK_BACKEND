package kh.edu.acleda.campuslink.modules.finance.enums;

public enum TransactionType {
    MOCK_KHQR_PAYMENT,
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
