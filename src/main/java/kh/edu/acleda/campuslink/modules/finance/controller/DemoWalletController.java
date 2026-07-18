package kh.edu.acleda.campuslink.modules.finance.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kh.edu.acleda.campuslink.modules.finance.dto.request.WalletPaymentRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.request.WalletTopUpRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.TransactionResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.WalletResponse;
import kh.edu.acleda.campuslink.modules.finance.service.DemoWalletService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/finance/wallet")
@Tag(name = "Finance - Demo Wallet")
public class DemoWalletController {

    private final DemoWalletService demoWalletService;

    public DemoWalletController(DemoWalletService demoWalletService) {
        this.demoWalletService = demoWalletService;
    }

    /** The current user's wallet, created on first access. */
    @GetMapping
    public ResponseEntity<ApiResponse<WalletResponse>> get() {
        return ResponseEntity.ok(ApiResponse.success("Wallet retrieved", demoWalletService.getOrCreate()));
    }

    @PostMapping("/top-up")
    public ResponseEntity<ApiResponse<WalletResponse>> topUp(@Valid @RequestBody WalletTopUpRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Wallet topped up (simulated)",
            demoWalletService.topUp(request)));
    }

    @PostMapping("/pay")
    public ResponseEntity<ApiResponse<WalletResponse>> pay(@Valid @RequestBody WalletPaymentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Payment completed (simulated)",
            demoWalletService.pay(request)));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> transactions(
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Transactions retrieved",
            demoWalletService.transactions(pageable)));
    }
}
