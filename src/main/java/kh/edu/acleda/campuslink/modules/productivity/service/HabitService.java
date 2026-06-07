package kh.edu.acleda.campuslink.modules.productivity.service;

import kh.edu.acleda.campuslink.modules.productivity.dto.request.HabitCreateRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.HabitResponse;

import java.util.List;
import java.util.UUID;

public interface HabitService {
    HabitResponse create(HabitCreateRequest request);
    List<HabitResponse> getMine();
    HabitResponse update(UUID id, HabitCreateRequest request);
    void delete(UUID id);
}
