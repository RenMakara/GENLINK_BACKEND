package kh.edu.acleda.campuslink.modules.finance.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.SavingDepositRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.request.SavingGoalCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.SavingDepositResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.SavingGoalResponse;
import kh.edu.acleda.campuslink.modules.finance.enums.SavingGoalStatus;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/finance/savings")
@Tag(name = "Finance - Savings")
public class SavingGoalController {

    private final SavingGoalService savingGoalService;

    public SavingGoalController(SavingGoalService savingGoalService) {
        this.savingGoalService = savingGoalService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SavingGoalResponse>> create(
        @Valid @RequestBody SavingGoalCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Saving goal created", savingGoalService.create(request)));
    }

    /** Newest first; optional ?status=IN_PROGRESS|COMPLETED|CANCELLED and ?currency=USD|KHR. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SavingGoalResponse>>> getMine(
        @RequestParam(required = false) SavingGoalStatus status,
        @RequestParam(required = false) Currency currency) {
        return ResponseEntity.ok(ApiResponse.success("Saving goals retrieved",
            savingGoalService.getMine(status, currency)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SavingGoalResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Saving goal retrieved", savingGoalService.getById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SavingGoalResponse>> update(@PathVariable UUID id,
                                                                  @Valid @RequestBody SavingGoalCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Saving goal updated", savingGoalService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        savingGoalService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Saving goal deleted", null));
    }

    /** Add money to a goal; auto-completes it when the target is reached. */
    @PostMapping("/{id}/deposits")
    public ResponseEntity<ApiResponse<SavingGoalResponse>> deposit(@PathVariable UUID id,
                                                                   @Valid @RequestBody SavingDepositRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Deposit recorded", savingGoalService.deposit(id, request)));
    }

    @GetMapping("/{id}/deposits")
    public ResponseEntity<ApiResponse<List<SavingDepositResponse>>> deposits(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Deposits retrieved", savingGoalService.deposits(id)));
    }
}
