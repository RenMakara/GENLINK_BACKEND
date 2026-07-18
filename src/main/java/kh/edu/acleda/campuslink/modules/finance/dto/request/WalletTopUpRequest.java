package kh.edu.acleda.campuslink.modules.finance.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Simulated deposit into the demo wallet. No real money moves. */
public record WalletTopUpRequest(
    @NotNull @Positive BigDecimal amount,
    @Size(max = 200) String note
) {
}
