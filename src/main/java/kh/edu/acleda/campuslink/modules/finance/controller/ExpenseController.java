package kh.edu.acleda.campuslink.modules.finance.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kh.edu.acleda.campuslink.modules.finance.dto.request.ExpenseCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.ExpenseResponse;
import kh.edu.acleda.campuslink.modules.finance.service.ExpenseService;
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
@RequestMapping("/api/v1/finance/expenses")
@Tag(name = "Finance - Expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ExpenseResponse>> create(@Valid @RequestBody ExpenseCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Expense created", expenseService.create(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExpenseResponse>>> getMine() {
        return ResponseEntity.ok(ApiResponse.success("Expense list retrieved", expenseService.getMine()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ExpenseResponse>> update(@PathVariable UUID id, @Valid @RequestBody ExpenseCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Expense updated", expenseService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        expenseService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Expense deleted", null));
    }
}
