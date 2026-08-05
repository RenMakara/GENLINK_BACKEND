package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionDirection;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionStatus;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;

public record TransactionResponse(
    UUID transactionId,
    TransactionType transactionType,
    TransactionDirection direction,
    Currency currency,
    BigDecimal amount,
    String description,
    TransactionStatus status,
    Instant createdAt,
    String provider,
    String providerRef
) {
}
