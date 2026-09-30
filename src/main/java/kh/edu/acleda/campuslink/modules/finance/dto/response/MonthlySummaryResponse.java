package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.math.BigDecimal;
import java.util.List;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

/** Spending for one month: the total plus a per-category breakdown for the chart. */
public record MonthlySummaryResponse(
    int month,
    int year,
    Currency currency,
    BigDecimal total,
    List<CategoryTotal> byCategory
) {

    public record CategoryTotal(String category, BigDecimal total) {}
}
