package kh.edu.acleda.campuslink.modules.finance.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

/**
 * @param categories optional per-category allocations. Their sum is not required to
 *                   equal totalBudget - a user may budget only some categories.
 */
public record BudgetCreateRequest(
    @NotNull Integer month,
    @NotNull Integer year,
    @NotNull @Positive BigDecimal totalBudget,
    @Valid List<BudgetCategoryRequest> categories
) {
}
