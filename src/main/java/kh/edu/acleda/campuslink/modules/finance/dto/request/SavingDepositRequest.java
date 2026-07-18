package kh.edu.acleda.campuslink.modules.finance.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param depositDate optional; defaults to today when omitted. Lets a user backdate a
 *                    deposit they actually made earlier.
 */
public record SavingDepositRequest(
    @NotNull @Positive BigDecimal amount,
    @Size(max = 500) String note,
    LocalDate depositDate
) {
}
