package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Spending for one month: the total plus a per-category breakdown for the chart.
 *
 * @param byCategory biggest first; a null category (expense saved without one) is
 *                   reported as "UNCATEGORIZED" so the client never renders a blank slice
 */
public record MonthlySummaryResponse(
    int month,
    int year,
    BigDecimal total,
    List<CategoryTotal> byCategory
) {

    public record CategoryTotal(String category, BigDecimal total) {}
}
