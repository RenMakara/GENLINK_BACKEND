package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.math.BigDecimal;

/**
 * A month's money in vs out. {@code net} is what is left (income - expense), and can go
 * negative when overspent. {@code savingsRatePercent} is net as a share of income - the single
 * number financial-literacy teaching cares about most - and is 0 when there is no income.
 */
public record CashFlowResponse(
    int month,
    int year,
    BigDecimal totalIncome,
    BigDecimal totalExpense,
    BigDecimal net,
    BigDecimal savingsRatePercent
) {
}
