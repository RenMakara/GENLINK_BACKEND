package kh.edu.acleda.campuslink.modules.finance.enums;

public enum TransactionType {
    /** A KHQR payment. */
    KHQR_PAYMENT,
    EVENT_PAYMENT,
    DEPOSIT,
    /** Simulated learning-budget credit for passing a financial-literacy quiz. */
    REWARD,
    /** A recorded expense. */
    EXPENSE,
    /** Income such as allowance, wages, or scholarships. */
    INCOME,
    /** Money added to a savings goal. */
    SAVING
}
