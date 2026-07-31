package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

/**
 * A month's money read as a short list of plain-language nudges, computed live over the
 * income/expense/goal data (never stored). Everything is per currency - mixing KHR and USD
 * would make the figures meaningless.
 *
 * @param currency           the currency these insights are about
 * @param month              1-12
 * @param year               four-digit year
 * @param income             total income this month
 * @param expense            total expense this month
 * @param net                income - expense (negative = overspent)
 * @param savingsRatePercent net / income * 100 (0 when there is no income)
 * @param insights           ordered most-urgent first; empty when there is nothing to say
 */
public record FinanceInsightsResponse(
    Currency currency,
    int month,
    int year,
    BigDecimal income,
    BigDecimal expense,
    BigDecimal net,
    BigDecimal savingsRatePercent,
    List<Insight> insights
) {

    /**
     * One nudge.
     *
     * @param type     stable machine key (e.g. CATEGORY_SPIKE) the client maps to an icon
     * @param severity CRITICAL | WARNING | GOOD | INFO - drives colour/sort
     * @param title    short headline
     * @param message  one-sentence explanation, already localized to English
     * @param category the spending category this is about, or null
     * @param amount   the figure this is about (spend, shortfall, ...), or null
     * @param lesson   a matched literacy lesson to act on it, or null
     */
    public record Insight(
        String type,
        String severity,
        String title,
        String message,
        String category,
        BigDecimal amount,
        SuggestedLesson lesson
    ) {}

    /** A pointer to a financial-literacy lesson (English title; full content via the literacy API). */
    public record SuggestedLesson(UUID lessonId, String title, String topic) {}
}
