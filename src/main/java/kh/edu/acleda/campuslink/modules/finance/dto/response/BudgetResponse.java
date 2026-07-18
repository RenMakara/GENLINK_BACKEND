package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.enums.BudgetStatus;

/**
 * spentAmount, remainingAmount and status are computed live from expenses, not read
 * from the stored counters - so they never drift.
 *
 * @param categories empty on the list endpoint (kept light); populated on the detail
 *                   and current-month endpoints
 */
public record BudgetResponse(
    UUID budgetId,
    Integer month,
    Integer year,
    BigDecimal totalBudget,
    BigDecimal spentAmount,
    BigDecimal remainingAmount,
    BudgetStatus status,
    List<CategoryBudget> categories
) {

    public record CategoryBudget(
        String category,
        BigDecimal allocatedAmount,
        BigDecimal spentAmount,
        BigDecimal remainingAmount
    ) {}
}
