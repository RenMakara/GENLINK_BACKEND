package kh.edu.acleda.campuslink.modules.finance.service.impl;

import kh.edu.acleda.campuslink.modules.finance.dto.request.SavingGoalCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.SavingGoalResponse;
import kh.edu.acleda.campuslink.modules.finance.service.SavingGoalService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SavingGoalServiceImpl implements SavingGoalService {

    @Override
    public SavingGoalResponse create(SavingGoalCreateRequest request) {
        // TODO: Implement SavingGoal creation using CurrentUserProvider and repository.
        return new SavingGoalResponse(null, request.title(), request.targetAmount(), java.math.BigDecimal.ZERO, request.targetDate(), kh.edu.acleda.campuslink.modules.finance.enums.SavingGoalStatus.IN_PROGRESS);
    }

    @Override
    public List<SavingGoalResponse> getMine() {
        // TODO: Load authenticated user's SavingGoal records.
        return List.of();
    }

    @Override
    public SavingGoalResponse update(UUID id, SavingGoalCreateRequest request) {
        // TODO: Validate ownership, update allowed fields, and persist.
        return new SavingGoalResponse(null, request.title(), request.targetAmount(), java.math.BigDecimal.ZERO, request.targetDate(), kh.edu.acleda.campuslink.modules.finance.enums.SavingGoalStatus.IN_PROGRESS);
    }

    @Override
    public void delete(UUID id) {
        // TODO: Soft delete the SavingGoal record after ownership validation.
    }
}
