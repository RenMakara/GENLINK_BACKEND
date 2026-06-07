package kh.edu.acleda.campuslink.modules.finance.service;

import kh.edu.acleda.campuslink.modules.finance.dto.request.SavingGoalCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.SavingGoalResponse;

import java.util.List;
import java.util.UUID;

public interface SavingGoalService {
    SavingGoalResponse create(SavingGoalCreateRequest request);
    List<SavingGoalResponse> getMine();
    SavingGoalResponse update(UUID id, SavingGoalCreateRequest request);
    void delete(UUID id);
}
