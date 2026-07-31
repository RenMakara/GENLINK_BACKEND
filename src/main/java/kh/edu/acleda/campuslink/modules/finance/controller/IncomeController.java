package kh.edu.acleda.campuslink.modules.finance.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.IncomeCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.CashFlowResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.IncomeResponse;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.service.IncomeService;
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
@RequestMapping("/api/v1/finance/incomes")
@Tag(name = "Finance - Income")
public class IncomeController {

    private final IncomeService incomeService;

    public IncomeController(IncomeService incomeService) {
        this.incomeService = incomeService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<IncomeResponse>> create(@Valid @RequestBody IncomeCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Income created", incomeService.create(request)));
    }

    /** Newest first. Optional filters: ?source, and ?month + ?year together. */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<IncomeResponse>>> getMine(
        @RequestParam(required = false) String source,
        @RequestParam(required = false) Integer month,
        @RequestParam(required = false) Integer year,
        @PageableDefault(size = 20, sort = "incomeDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Income retrieved",
            incomeService.getMine(source, month, year, pageable)));
    }

    /**
     * Cash flow for a month: income vs expense, net, and savings rate. Defaults to the current
     * month. Literal path, declared before /{id} so it cannot be shadowed.
     */
    @GetMapping("/cash-flow")
    public ResponseEntity<ApiResponse<CashFlowResponse>> cashFlow(
        @RequestParam(required = false) Currency currency,
        @RequestParam(required = false) Integer month,
        @RequestParam(required = false) Integer year) {
        LocalDate now = LocalDate.now();
        Currency c = currency == null ? Currency.USD : currency;
        int m = month == null ? now.getMonthValue() : month;
        int y = year == null ? now.getYear() : year;
        return ResponseEntity.ok(ApiResponse.success("Cash flow retrieved", incomeService.cashFlow(c, m, y)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<IncomeResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Income retrieved", incomeService.getById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<IncomeResponse>> update(@PathVariable UUID id,
                                                              @Valid @RequestBody IncomeCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Income updated", incomeService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        incomeService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Income deleted", null));
    }
}
