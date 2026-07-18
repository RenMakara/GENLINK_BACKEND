package kh.edu.acleda.campuslink.modules.productivity.service;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.HabitCheckInRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.HabitCreateRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.HabitResponse;

public interface HabitService {

    HabitResponse create(HabitCreateRequest request);

    /** The user's habits, newest first, each with computed streak/rate. */
    List<HabitResponse> getMine();

    HabitResponse getById(UUID id);

    HabitResponse update(UUID id, HabitCreateRequest request);

    void delete(UUID id);

    /** Idempotent daily check-in (or undo). Returns the habit with the new streak. */
    HabitResponse checkIn(UUID id, HabitCheckInRequest request);
}
