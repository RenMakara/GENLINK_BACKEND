package kh.edu.acleda.campuslink.modules.finance.service;

import kh.edu.acleda.campuslink.modules.finance.dto.request.WalletPaymentRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.request.WalletTopUpRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.TransactionResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.WalletResponse;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

/** Demo wallet - a simulated balance. No real money, no real KHQR network. */
public interface DemoWalletService {

    /** The current user's wallet, created on first access. */
    WalletResponse getOrCreate();

    /** Get-or-create the current user's wallet ENTITY. */
    DemoWallet getOrCreateWalletEntity();

    /** Get-or-create a specific user's wallet ENTITY. */
    DemoWallet getOrCreateWalletEntity(User user);

    /** Simulated deposit. */
    WalletResponse topUp(WalletTopUpRequest request);

    /** Simulated KHQR payment; debits the balance. */
    WalletResponse pay(WalletPaymentRequest request);

    /** Credits a simulated reward to the current wallet. */
    WalletResponse creditReward(java.math.BigDecimal amount, String reason);

    PageResponse<TransactionResponse> transactions(Pageable pageable);
}
