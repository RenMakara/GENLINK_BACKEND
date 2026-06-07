package kh.edu.acleda.campuslink.modules.productivity.service;

import kh.edu.acleda.campuslink.modules.productivity.dto.request.GoalCreateRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.GoalResponse;

import java.util.List;
import java.util.UUID;

public interface GoalService {
    GoalResponse create(GoalCreateRequest request);
    List<GoalResponse> getMine();
    GoalResponse update(UUID id, GoalCreateRequest request);
    void delete(UUID id);
}
