package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.math.BigDecimal;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.enums.BudgetStatus;

public record BudgetResponse(
    UUID budgetId,
    Integer month,
    Integer year,
    BigDecimal totalBudget,
    BigDecimal spentAmount,
    BigDecimal remainingAmount,
    BudgetStatus status
) {
}
