package kh.edu.acleda.campuslink.modules.productivity.service;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.GoalCreateRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.MilestoneCreateRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.GoalResponse;

public interface GoalService {

    GoalResponse create(GoalCreateRequest request);

    List<GoalResponse> getMine();

    GoalResponse getById(UUID id);

    GoalResponse update(UUID id, GoalCreateRequest request);

    void delete(UUID id);

    GoalResponse addMilestone(UUID goalId, MilestoneCreateRequest request);

    /** Complete or reopen a milestone; recomputes the goal's progress/status. */
    GoalResponse setMilestoneCompleted(UUID goalId, UUID milestoneId, boolean completed);

    GoalResponse deleteMilestone(UUID goalId, UUID milestoneId);
}
