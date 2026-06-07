package kh.edu.acleda.campuslink.modules.finance.service.impl;

import kh.edu.acleda.campuslink.modules.finance.dto.request.ExpenseCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.ExpenseResponse;
import kh.edu.acleda.campuslink.modules.finance.service.ExpenseService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ExpenseServiceImpl implements ExpenseService {

    @Override
    public ExpenseResponse create(ExpenseCreateRequest request) {
        // TODO: Implement Expense creation using CurrentUserProvider and repository.
        return new ExpenseResponse(null, request.title(), request.amount(), request.category(), request.description(), request.expenseDate(), request.paymentMethod());
    }

    @Override
    public List<ExpenseResponse> getMine() {
        // TODO: Load authenticated user's Expense records.
        return List.of();
    }

    @Override
    public ExpenseResponse update(UUID id, ExpenseCreateRequest request) {
        // TODO: Validate ownership, update allowed fields, and persist.
        return new ExpenseResponse(null, request.title(), request.amount(), request.category(), request.description(), request.expenseDate(), request.paymentMethod());
    }

    @Override
    public void delete(UUID id) {
        // TODO: Soft delete the Expense record after ownership validation.
    }
}
