package kh.edu.acleda.campuslink.modules.finance.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

/** A month's money read as a short list of plain-language nudges. */
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

    /** One nudge. */
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
