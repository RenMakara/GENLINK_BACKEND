package kh.edu.acleda.campuslink.modules.finance.service;

import kh.edu.acleda.campuslink.modules.finance.dto.request.BudgetCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.BudgetResponse;

import java.util.List;
import java.util.UUID;

public interface BudgetService {
    BudgetResponse create(BudgetCreateRequest request);
    List<BudgetResponse> getMine();
    BudgetResponse update(UUID id, BudgetCreateRequest request);
    void delete(UUID id);
}
