package kh.edu.acleda.campuslink.modules.productivity.service.impl;

import kh.edu.acleda.campuslink.modules.productivity.dto.request.HabitCreateRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.HabitResponse;
import kh.edu.acleda.campuslink.modules.productivity.service.HabitService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class HabitServiceImpl implements HabitService {

    @Override
    public HabitResponse create(HabitCreateRequest request) {
        // TODO: Implement Habit creation using CurrentUserProvider and repository.
        return new HabitResponse(null, request.title(), request.category(), request.frequency(), 0, null);
    }

    @Override
    public List<HabitResponse> getMine() {
        // TODO: Load authenticated user's Habit records.
        return List.of();
    }

    @Override
    public HabitResponse update(UUID id, HabitCreateRequest request) {
        // TODO: Validate ownership, update allowed fields, and persist.
        return new HabitResponse(null, request.title(), request.category(), request.frequency(), 0, null);
    }

    @Override
    public void delete(UUID id) {
        // TODO: Soft delete the Habit record after ownership validation.
    }
}
