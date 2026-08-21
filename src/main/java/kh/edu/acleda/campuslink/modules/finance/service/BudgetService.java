package kh.edu.acleda.campuslink.modules.finance.service;

import kh.edu.acleda.campuslink.modules.finance.dto.request.BudgetCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.BudgetResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

public interface BudgetService {

    /** One budget per user, month/year, and currency. */
    BudgetResponse create(BudgetCreateRequest request);

    /** List, newest first. Spend is computed live; categories omitted to stay light. */
    PageResponse<BudgetResponse> getMine(Pageable pageable);

    BudgetResponse getById(UUID id);

    /** The budget for a given month, with category breakdown. */
    BudgetResponse getForMonth(int month, int year, Currency currency);

    /** Backward-compatible default for callers that predate dual-currency budgets. */
    default BudgetResponse getForMonth(int month, int year) {
        return getForMonth(month, year, Currency.USD);
    }

    BudgetResponse update(UUID id, BudgetCreateRequest request);

    void delete(UUID id);
}
