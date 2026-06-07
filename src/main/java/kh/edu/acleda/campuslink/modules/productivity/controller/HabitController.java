package kh.edu.acleda.campuslink.modules.productivity.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.HabitCreateRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.HabitResponse;
import kh.edu.acleda.campuslink.modules.productivity.service.HabitService;
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
@RequestMapping("/api/v1/productivity/habits")
@Tag(name = "Productivity - Habits")
public class HabitController {

    private final HabitService habitService;

    public HabitController(HabitService habitService) {
        this.habitService = habitService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HabitResponse>> create(@Valid @RequestBody HabitCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Habit created", habitService.create(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<HabitResponse>>> getMine() {
        return ResponseEntity.ok(ApiResponse.success("Habit list retrieved", habitService.getMine()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HabitResponse>> update(@PathVariable UUID id, @Valid @RequestBody HabitCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Habit updated", habitService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        habitService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Habit deleted", null));
    }
}
