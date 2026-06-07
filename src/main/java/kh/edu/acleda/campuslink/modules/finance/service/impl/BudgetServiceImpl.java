package kh.edu.acleda.campuslink.modules.finance.service.impl;

import kh.edu.acleda.campuslink.modules.finance.dto.request.BudgetCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.BudgetResponse;
import kh.edu.acleda.campuslink.modules.finance.service.BudgetService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class BudgetServiceImpl implements BudgetService {

    @Override
    public BudgetResponse create(BudgetCreateRequest request) {
        // TODO: Implement Budget creation using CurrentUserProvider and repository.
        return new BudgetResponse(null, request.month(), request.year(), request.totalBudget(), java.math.BigDecimal.ZERO, request.totalBudget(), kh.edu.acleda.campuslink.modules.finance.enums.BudgetStatus.SAFE);
    }

    @Override
    public List<BudgetResponse> getMine() {
        // TODO: Load authenticated user's Budget records.
        return List.of();
    }

    @Override
    public BudgetResponse update(UUID id, BudgetCreateRequest request) {
        // TODO: Validate ownership, update allowed fields, and persist.
        return new BudgetResponse(null, request.month(), request.year(), request.totalBudget(), java.math.BigDecimal.ZERO, request.totalBudget(), kh.edu.acleda.campuslink.modules.finance.enums.BudgetStatus.SAFE);
    }

    @Override
    public void delete(UUID id) {
        // TODO: Soft delete the Budget record after ownership validation.
    }
}
