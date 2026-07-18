package kh.edu.acleda.campuslink.modules.productivity.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.TaskCreateRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.TaskResponse;
import kh.edu.acleda.campuslink.modules.productivity.service.TaskService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/productivity/tasks")
@Tag(name = "Productivity - Tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> create(@Valid @RequestBody TaskCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Task created", taskService.create(request)));
    }

    /** Filters (all optional): category, priority, completed, scope=today|overdue|upcoming. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getMine(
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String priority,
        @RequestParam(required = false) Boolean completed,
        @RequestParam(required = false) String scope) {
        return ResponseEntity.ok(ApiResponse.success("Tasks retrieved",
            taskService.getMine(category, priority, completed, scope)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Task retrieved", taskService.getById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> update(@PathVariable UUID id,
                                                            @Valid @RequestBody TaskCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Task updated", taskService.update(id, request)));
    }

    /** Mark complete (default) or reopen with ?completed=false. */
    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<TaskResponse>> setCompleted(@PathVariable UUID id,
                                                                  @RequestParam(defaultValue = "true") boolean completed) {
        return ResponseEntity.ok(ApiResponse.success("Task updated", taskService.setCompleted(id, completed)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        taskService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Task deleted", null));
    }
}
