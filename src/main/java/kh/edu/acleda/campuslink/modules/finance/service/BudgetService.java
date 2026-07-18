package kh.edu.acleda.campuslink.modules.finance.service;

import kh.edu.acleda.campuslink.modules.finance.dto.request.BudgetCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.BudgetResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface BudgetService {

    /** One budget per user per month/year. */
    BudgetResponse create(BudgetCreateRequest request);

    /** List, newest first. Spend is computed live; categories omitted to stay light. */
    PageResponse<BudgetResponse> getMine(Pageable pageable);

    BudgetResponse getById(UUID id);

    /** The budget for a given month, with category breakdown. */
    BudgetResponse getForMonth(int month, int year);

    BudgetResponse update(UUID id, BudgetCreateRequest request);

    void delete(UUID id);
}
