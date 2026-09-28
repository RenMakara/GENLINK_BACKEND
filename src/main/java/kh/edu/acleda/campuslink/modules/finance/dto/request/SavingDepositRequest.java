package kh.edu.acleda.campuslink.modules.finance.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param depositDate optional; defaults to today when omitted. Lets a user backdate a
 *                    deposit they actually made earlier.
 * @param fromWallet  when true, the deposit also debits the demo wallet (rejected if funds are
 *                    too low). Default/false = the goal grows without touching the wallet.
 */
public record SavingDepositRequest(
    @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
    @Size(max = 500) String note,
    LocalDate depositDate,
    Boolean fromWallet
) {
}
