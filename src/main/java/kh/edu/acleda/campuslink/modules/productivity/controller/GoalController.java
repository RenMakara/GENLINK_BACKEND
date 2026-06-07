package kh.edu.acleda.campuslink.modules.productivity.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.GoalCreateRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.GoalResponse;
import kh.edu.acleda.campuslink.modules.productivity.service.GoalService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/productivity/goals")
@Tag(name = "Productivity - Goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GoalResponse>> create(@Valid @RequestBody GoalCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Goal created", goalService.create(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<GoalResponse>>> getMine() {
        return ResponseEntity.ok(ApiResponse.success("Goal list retrieved", goalService.getMine()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GoalResponse>> update(@PathVariable UUID id, @Valid @RequestBody GoalCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Goal updated", goalService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        goalService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Goal deleted", null));
    }
}
