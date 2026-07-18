package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletResponse(
    UUID walletId,
    BigDecimal balance,
    String currency
) {
}
