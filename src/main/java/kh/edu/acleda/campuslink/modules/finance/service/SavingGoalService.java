package kh.edu.acleda.campuslink.modules.finance.service;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.SavingDepositRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.request.SavingGoalCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.SavingDepositResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.SavingGoalResponse;
import kh.edu.acleda.campuslink.modules.finance.enums.SavingGoalStatus;

public interface SavingGoalService {

    SavingGoalResponse create(SavingGoalCreateRequest request);

    /** All of the user's goals, newest first; optionally filtered by status. */
    List<SavingGoalResponse> getMine(SavingGoalStatus status);

    SavingGoalResponse getById(UUID id);

    SavingGoalResponse update(UUID id, SavingGoalCreateRequest request);

    void delete(UUID id);

    /** Records a deposit, grows the goal, and completes it once the target is reached. */
    SavingGoalResponse deposit(UUID goalId, SavingDepositRequest request);

    /** Deposit history for a goal, most recent first. */
    List<SavingDepositResponse> deposits(UUID goalId);
}
