package kh.edu.acleda.campuslink.modules.productivity.service.impl;

import kh.edu.acleda.campuslink.modules.productivity.dto.request.TaskCreateRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.TaskResponse;
import kh.edu.acleda.campuslink.modules.productivity.service.TaskService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class TaskServiceImpl implements TaskService {

    @Override
    public TaskResponse create(TaskCreateRequest request) {
        // TODO: Implement Task creation using CurrentUserProvider and repository.
        return new TaskResponse(null, request.title(), request.description(), request.priority(), request.category(), request.dueDate(), false);
    }

    @Override
    public List<TaskResponse> getMine() {
        // TODO: Load authenticated user's Task records.
        return List.of();
    }

    @Override
    public TaskResponse update(UUID id, TaskCreateRequest request) {
        // TODO: Validate ownership, update allowed fields, and persist.
        return new TaskResponse(null, request.title(), request.description(), request.priority(), request.category(), request.dueDate(), false);
    }

    @Override
    public void delete(UUID id) {
        // TODO: Soft delete the Task record after ownership validation.
    }
}
