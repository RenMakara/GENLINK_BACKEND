package kh.edu.acleda.campuslink.modules.finance.service;

import kh.edu.acleda.campuslink.modules.finance.dto.response.FinanceInsightsResponse;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

/**
 * Turns the current user's finance data into a short list of actionable nudges - category
 * spikes, spending trend, savings rate, and saving-goal pace - each optionally matched to a
 * literacy lesson. Read-only aggregation; writes nothing.
 */
public interface InsightService {

    /** Insights for a month, in the given currency. */
    FinanceInsightsResponse getInsights(Currency currency, int month, int year);
}
