package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.math.BigDecimal;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

/** A month's money in vs out, for one {@code currency} (mixing KHR and USD in one total would be meaningless). */
public record CashFlowResponse(
    Currency currency,
    int month,
    int year,
    BigDecimal totalIncome,
    BigDecimal totalExpense,
    BigDecimal net,
    BigDecimal savingsRatePercent
) {
}
