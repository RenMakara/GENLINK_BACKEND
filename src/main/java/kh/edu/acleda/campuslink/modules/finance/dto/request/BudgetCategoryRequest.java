package kh.edu.acleda.campuslink.modules.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** One per-category allocation inside a monthly budget. */
public record BudgetCategoryRequest(
    @NotBlank @Size(max = 80) String category,
    @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal allocatedAmount
) {
}
