package kh.edu.acleda.campuslink.modules.mobile.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

/** The Finance screen in one call (BFF-lite). */
public record MobileFinanceDashboardResponse(
    Currency currency,
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
        Currency currency,
        BigDecimal spent,
        BigDecimal budgetTotal,
        BigDecimal budgetRemaining,
        String budgetStatus
    ) {}

    public record CategorySpend(String category, Currency currency, BigDecimal amount) {}

    public record SavingProgress(
        Currency currency,
        BigDecimal totalTarget,
        BigDecimal totalSaved,
        int progressPercent,
        int activeGoals
    ) {}

    public record RecentExpense(
        UUID expenseId,
        String title,
        String category,
        Currency currency,
        BigDecimal amount,
        LocalDate expenseDate
    ) {}

    public record WalletSummary(BigDecimal balanceUsd, BigDecimal balanceKhr) {}
}
