package kh.edu.acleda.campuslink.modules.finance.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.BudgetCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.BudgetResponse;
import kh.edu.acleda.campuslink.modules.finance.service.BudgetService;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

    /** Newest first. Spend/status computed live; category breakdown omitted here. */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BudgetResponse>>> getMine(
        @PageableDefault(size = 20, sort = "year", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Budgets retrieved", budgetService.getMine(pageable)));
    }

    /**
     * The budget for a month, with the category breakdown. Defaults to the current
     * month. Literal path, declared before /{id} so it cannot be shadowed.
     */
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<BudgetResponse>> current(
        @RequestParam(required = false) Integer month,
        @RequestParam(required = false) Integer year,
        @RequestParam(defaultValue = "USD") Currency currency) {
        LocalDate now = LocalDate.now();
        int m = month == null ? now.getMonthValue() : month;
        int y = year == null ? now.getYear() : year;
        return ResponseEntity.ok(ApiResponse.success("Budget retrieved", budgetService.getForMonth(m, y, currency)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BudgetResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Budget retrieved", budgetService.getById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BudgetResponse>> update(@PathVariable UUID id,
                                                              @Valid @RequestBody BudgetCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Budget updated", budgetService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        budgetService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Budget deleted", null));
    }
}
