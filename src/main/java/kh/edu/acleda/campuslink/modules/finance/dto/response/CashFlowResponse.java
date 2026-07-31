package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.math.BigDecimal;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

/**
 * A month's money in vs out, for one {@code currency} (mixing KHR and USD in one total would be
 * meaningless). {@code net} is what is left (income - expense), and can go negative when
 * overspent. {@code savingsRatePercent} is net as a share of income - the single number
 * financial-literacy teaching cares about most - and is 0 when there is no income.
 */
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
