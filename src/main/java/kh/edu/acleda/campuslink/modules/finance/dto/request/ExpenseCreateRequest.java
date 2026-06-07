package kh.edu.acleda.campuslink.modules.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseCreateRequest(
    @NotBlank @Size(max = 160) String title,
    @NotNull @Positive BigDecimal amount,
    String category,
    String description,
    @NotNull LocalDate expenseDate,
    String paymentMethod
) {
}
