package kh.edu.acleda.campuslink.modules.finance.service;

import java.time.LocalDate;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.entity.Expense;
import kh.edu.acleda.campuslink.modules.finance.enums.BudgetStatus;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

/**
 * Evaluates live budget thresholds around an expense mutation and emits crossing alerts.
 * Notification persistence is delegated to the notification module.
 */
public interface BudgetAlertService {

    /** Returns the live status before an expense is created or moved into a scope. */
    BudgetStatus statusBeforeExpenseMutation(UUID userId, LocalDate expenseDate, Currency currency);

    /** Evaluates the live status after the expense mutation has completed successfully. */
    void evaluateAfterExpenseMutation(Expense expense, BudgetStatus statusBefore);
}
