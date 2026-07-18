package kh.edu.acleda.campuslink.modules.mobile.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The Finance screen in one call (BFF-lite). Every section degrades independently: a
 * user with no budget/wallet/goals still gets a well-formed body with nulls and empty
 * lists, so the client can render empty states without a second round-trip.
 *
 * @param month          this month's spend and budget standing (budget fields null if unset)
 * @param topCategories  biggest spending categories this month, largest first
 * @param saving         aggregate progress across active goals
 * @param recentExpenses the latest few expenses
 * @param wallet         demo wallet balance, or null if the user has never opened one
 * @param financialTip   a rotating tip (English only for now - see FINANCE_PLAN)
 * @param quickActions   action keys the client maps to localized buttons
 */
public record MobileFinanceDashboardResponse(
    MonthOverview month,
    List<CategorySpend> topCategories,
    SavingProgress saving,
    List<RecentExpense> recentExpenses,
    WalletSummary wallet,
    String financialTip,
    List<String> quickActions
) {

    public record MonthOverview(
        int month,
        int year,
        BigDecimal spent,
        BigDecimal budgetTotal,
        BigDecimal budgetRemaining,
        String budgetStatus
    ) {}

    public record CategorySpend(String category, BigDecimal amount) {}

    public record SavingProgress(
        BigDecimal totalTarget,
        BigDecimal totalSaved,
        int progressPercent,
        int activeGoals
    ) {}

    public record RecentExpense(
        UUID expenseId,
        String title,
        String category,
        BigDecimal amount,
        LocalDate expenseDate
    ) {}

    public record WalletSummary(BigDecimal balance, String currency) {}
}
