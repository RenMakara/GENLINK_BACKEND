package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SavingDepositResponse(
    UUID depositId,
    BigDecimal amount,
    String note,
    LocalDate depositDate
) {
}
