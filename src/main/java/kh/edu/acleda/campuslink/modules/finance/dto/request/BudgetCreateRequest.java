package kh.edu.acleda.campuslink.modules.finance.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

/** @param categories optional per-category allocations. Their sum is not required to */
public record BudgetCreateRequest(
    @NotNull Integer month,
    @NotNull Integer year,
    @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal totalBudget,
    @Valid List<BudgetCategoryRequest> categories,
    Currency currency
) {
}
