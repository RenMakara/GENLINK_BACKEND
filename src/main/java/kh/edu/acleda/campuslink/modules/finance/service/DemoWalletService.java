package kh.edu.acleda.campuslink.modules.finance.service;

import kh.edu.acleda.campuslink.modules.finance.dto.request.WalletPaymentRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.request.WalletTopUpRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.TransactionResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.WalletResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

/** Demo wallet - a simulated balance. No real money, no real KHQR network. */
public interface DemoWalletService {

    /** The current user's wallet, created on first access. */
    WalletResponse getOrCreate();

    /** Simulated deposit. */
    WalletResponse topUp(WalletTopUpRequest request);

    /** Simulated KHQR payment; debits the balance. */
    WalletResponse pay(WalletPaymentRequest request);

    PageResponse<TransactionResponse> transactions(Pageable pageable);
}
