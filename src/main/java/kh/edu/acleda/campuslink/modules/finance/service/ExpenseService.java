package kh.edu.acleda.campuslink.modules.finance.service;

import kh.edu.acleda.campuslink.modules.finance.dto.request.ExpenseCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.ExpenseResponse;

import java.util.List;
import java.util.UUID;

public interface ExpenseService {
    ExpenseResponse create(ExpenseCreateRequest request);
    List<ExpenseResponse> getMine();
    ExpenseResponse update(UUID id, ExpenseCreateRequest request);
    void delete(UUID id);
}
