package kh.edu.acleda.campuslink.modules.finance.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.RecurringTransactionCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.RecurringTransactionResponse;
import kh.edu.acleda.campuslink.modules.finance.service.RecurringTransactionService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/finance/recurring")
@Tag(name = "Finance - Recurring Transactions")
public class RecurringTransactionController {

    private final RecurringTransactionService recurringTransactionService;

    public RecurringTransactionController(RecurringTransactionService recurringTransactionService) {
        this.recurringTransactionService = recurringTransactionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RecurringTransactionResponse>> create(
        @Valid @RequestBody RecurringTransactionCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Recurring transaction created",
            recurringTransactionService.create(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RecurringTransactionResponse>>> getMine() {
        return ResponseEntity.ok(ApiResponse.success("Recurring transactions retrieved",
            recurringTransactionService.getMine()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        recurringTransactionService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Recurring transaction deleted", null));
    }
}
