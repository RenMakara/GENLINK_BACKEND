package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.enums.BudgetStatus;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

/** spentAmount, remainingAmount and status are computed live from expenses. */
public record BudgetResponse(
    UUID budgetId,
    Integer month,
    Integer year,
    BigDecimal totalBudget,
    BigDecimal spentAmount,
    BigDecimal remainingAmount,
    BudgetStatus status,
    Currency currency,
    List<CategoryBudget> categories
) {

    public record CategoryBudget(
        String category,
        BigDecimal allocatedAmount,
        BigDecimal spentAmount,
        BigDecimal remainingAmount
    ) {}
}
