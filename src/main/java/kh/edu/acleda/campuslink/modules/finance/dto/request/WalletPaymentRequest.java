package kh.edu.acleda.campuslink.modules.finance.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * A simulated KHQR payment. The client passes what it "scanned" - amount and merchant.
 * Nothing here talks to a real KHQR network; it only moves the demo balance.
 */
public record WalletPaymentRequest(
    @NotNull @Positive BigDecimal amount,
    @Size(max = 160) String merchant,
    @Size(max = 200) String note
) {
}
