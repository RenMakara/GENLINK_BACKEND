package kh.edu.acleda.campuslink.modules.finance.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kh.edu.acleda.campuslink.modules.finance.dto.request.SavingGoalCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.SavingGoalResponse;
import kh.edu.acleda.campuslink.modules.finance.service.SavingGoalService;
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
@RequestMapping("/api/v1/finance/savings")
@Tag(name = "Finance - Savings")
public class SavingGoalController {

    private final SavingGoalService savingGoalService;

    public SavingGoalController(SavingGoalService savingGoalService) {
        this.savingGoalService = savingGoalService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SavingGoalResponse>> create(@Valid @RequestBody SavingGoalCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("SavingGoal created", savingGoalService.create(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SavingGoalResponse>>> getMine() {
        return ResponseEntity.ok(ApiResponse.success("SavingGoal list retrieved", savingGoalService.getMine()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SavingGoalResponse>> update(@PathVariable UUID id, @Valid @RequestBody SavingGoalCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("SavingGoal updated", savingGoalService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        savingGoalService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("SavingGoal deleted", null));
    }
}
