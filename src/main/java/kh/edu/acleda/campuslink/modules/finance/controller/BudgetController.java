package kh.edu.acleda.campuslink.modules.finance.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kh.edu.acleda.campuslink.modules.finance.dto.request.BudgetCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.BudgetResponse;
import kh.edu.acleda.campuslink.modules.finance.service.BudgetService;
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
@RequestMapping("/api/v1/finance/budgets")
@Tag(name = "Finance - Budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BudgetResponse>> create(@Valid @RequestBody BudgetCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Budget created", budgetService.create(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BudgetResponse>>> getMine() {
        return ResponseEntity.ok(ApiResponse.success("Budget list retrieved", budgetService.getMine()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BudgetResponse>> update(@PathVariable UUID id, @Valid @RequestBody BudgetCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Budget updated", budgetService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        budgetService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Budget deleted", null));
    }
}
