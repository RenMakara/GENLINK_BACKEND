package kh.edu.acleda.campuslink.modules.finance.service;

import kh.edu.acleda.campuslink.modules.finance.dto.request.ExpenseCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.ExpenseResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.MonthlySummaryResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

public interface ExpenseService {

    ExpenseResponse create(ExpenseCreateRequest request);

    /** The caller's expenses, newest first. category, month, year and currency are optional filters. */
    PageResponse<ExpenseResponse> getMine(String category, Integer month, Integer year,
                                          Currency currency, Pageable pageable);

    default PageResponse<ExpenseResponse> getMine(String category, Integer month, Integer year,
                                                  Pageable pageable) {
        return getMine(category, month, year, null, pageable);
    }

    ExpenseResponse getById(UUID id);

    ExpenseResponse update(UUID id, ExpenseCreateRequest request);

    void delete(UUID id);

    /** Total and per-category breakdown for a month, for the spending chart. */
    MonthlySummaryResponse monthlySummary(int month, int year, Currency currency);

    /** Backward-compatible default for callers that predate currency-specific summaries. */
    default MonthlySummaryResponse monthlySummary(int month, int year) {
        return monthlySummary(month, year, Currency.USD);
    }
}
