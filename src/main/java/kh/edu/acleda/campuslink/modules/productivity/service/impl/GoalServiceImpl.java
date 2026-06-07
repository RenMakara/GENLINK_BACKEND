package kh.edu.acleda.campuslink.modules.productivity.service.impl;

import kh.edu.acleda.campuslink.modules.productivity.dto.request.GoalCreateRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.GoalResponse;
import kh.edu.acleda.campuslink.modules.productivity.service.GoalService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class GoalServiceImpl implements GoalService {

    @Override
    public GoalResponse create(GoalCreateRequest request) {
        // TODO: Implement Goal creation using CurrentUserProvider and repository.
        return new GoalResponse(null, request.title(), request.goalType(), null, "NOT_STARTED", request.deadline());
    }

    @Override
    public List<GoalResponse> getMine() {
        // TODO: Load authenticated user's Goal records.
        return List.of();
    }

    @Override
    public GoalResponse update(UUID id, GoalCreateRequest request) {
        // TODO: Validate ownership, update allowed fields, and persist.
        return new GoalResponse(null, request.title(), request.goalType(), null, "NOT_STARTED", request.deadline());
    }

    @Override
    public void delete(UUID id) {
        // TODO: Soft delete the Goal record after ownership validation.
    }
}
