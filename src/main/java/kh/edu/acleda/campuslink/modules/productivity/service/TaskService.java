package kh.edu.acleda.campuslink.modules.productivity.service;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.TaskCreateRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.TaskResponse;

public interface TaskService {

    TaskResponse create(TaskCreateRequest request);

    /** Owner's tasks, optionally filtered. Any filter may be null (ignored). */
    List<TaskResponse> getMine(String category, String priority, Boolean completed, String scope);

    TaskResponse getById(UUID id);

    TaskResponse update(UUID id, TaskCreateRequest request);

    /** Marks the task complete or reopens it. */
    TaskResponse setCompleted(UUID id, boolean completed);

    void delete(UUID id);
}
