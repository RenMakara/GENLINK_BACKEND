package kh.edu.acleda.campuslink.modules.productivity.service;

import kh.edu.acleda.campuslink.modules.productivity.dto.request.TaskCreateRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.TaskResponse;

import java.util.List;
import java.util.UUID;

public interface TaskService {
    TaskResponse create(TaskCreateRequest request);
    List<TaskResponse> getMine();
    TaskResponse update(UUID id, TaskCreateRequest request);
    void delete(UUID id);
}
