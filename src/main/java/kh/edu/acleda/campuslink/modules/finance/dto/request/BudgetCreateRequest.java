package kh.edu.acleda.campuslink.modules.finance.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record BudgetCreateRequest(
    @NotNull Integer month,
    @NotNull Integer year,
    @NotNull @Positive BigDecimal totalBudget
) {
}
