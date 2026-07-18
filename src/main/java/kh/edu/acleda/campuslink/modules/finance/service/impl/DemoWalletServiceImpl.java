package kh.edu.acleda.campuslink.modules.finance.service.impl;

import java.math.BigDecimal;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.WalletPaymentRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.request.WalletTopUpRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.TransactionResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.WalletResponse;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoTransaction;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionStatus;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;
import kh.edu.acleda.campuslink.modules.finance.repository.DemoTransactionRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.DemoWalletRepository;
import kh.edu.acleda.campuslink.modules.finance.service.DemoWalletService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoWalletServiceImpl implements DemoWalletService {

    private static final Logger log = LoggerFactory.getLogger(DemoWalletServiceImpl.class);

    /** Small USD amounts (event prices like 5.00) - not KHR, which is whole-riel. */
    private static final String DEFAULT_CURRENCY = "USD";

    private final DemoWalletRepository demoWalletRepository;
    private final DemoTransactionRepository demoTransactionRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public DemoWalletServiceImpl(DemoWalletRepository demoWalletRepository,
                                 DemoTransactionRepository demoTransactionRepository,
                                 UserRepository userRepository,
                                 CurrentUserProvider currentUserProvider) {
        this.demoWalletRepository = demoWalletRepository;
        this.demoTransactionRepository = demoTransactionRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public WalletResponse getOrCreate() {
        return toResponse(loadOrCreateWallet());
    }

    @Override
    @Transactional
    public WalletResponse topUp(WalletTopUpRequest request) {
        DemoWallet wallet = loadOrCreateWallet();
        wallet.setBalance(wallet.getBalance().add(request.amount()));
        demoWalletRepository.save(wallet);

        record(wallet, TransactionType.DEPOSIT, request.amount(),
            request.note() == null ? "Simulated top-up" : "Simulated top-up: " + request.note());
        log.info("SIMULATED wallet top-up: user={} amount={} balanceAfter={}",
            wallet.getUser().getId(), request.amount(), wallet.getBalance());
        return toResponse(wallet);
    }

    @Override
    @Transactional
    public WalletResponse pay(WalletPaymentRequest request) {
        DemoWallet wallet = loadOrCreateWallet();
        if (wallet.getBalance().compareTo(request.amount()) < 0) {
            // Throw before any write - no FAILED row, matching the paid-RSVP debit path.
            throw new ApiException("Demo wallet balance is too low for this payment",
                "DEMO_WALLET_INSUFFICIENT_BALANCE");
        }

        wallet.setBalance(wallet.getBalance().subtract(request.amount()));
        demoWalletRepository.save(wallet);

        String merchant = request.merchant() == null ? "merchant" : request.merchant();
        String description = "Simulated KHQR payment to " + merchant
            + (request.note() == null ? "" : " (" + request.note() + ")");
        record(wallet, TransactionType.MOCK_KHQR_PAYMENT, request.amount(), description);

        log.info("SIMULATED wallet payment: user={} amount={} merchant={} balanceAfter={}",
            wallet.getUser().getId(), request.amount(), merchant, wallet.getBalance());
        return toResponse(wallet);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> transactions(Pageable pageable) {
        DemoWallet wallet = demoWalletRepository.findByUserId(currentUserProvider.getCurrentUserId())
            .orElse(null);
        if (wallet == null) {
            // No wallet yet means no history - an empty page, not a 404.
            return PageResponse.from(org.springframework.data.domain.Page.empty(pageable));
        }
        return PageResponse.from(
            demoTransactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable),
            t -> new TransactionResponse(t.getId(), t.getTransactionType(), t.getAmount(),
                t.getDescription(), t.getStatus(), t.getCreatedAt()));
    }

    private void record(DemoWallet wallet, TransactionType type, BigDecimal amount, String description) {
        demoTransactionRepository.save(DemoTransaction.builder()
            .wallet(wallet)
            .transactionType(type)
            .amount(amount)
            .description(description)
            .status(TransactionStatus.SUCCESS)
            .build());
    }

    /**
     * Get-or-create the current user's wallet. UNIQUE(user_id) means two concurrent
     * first-access requests could both try to insert; the loser catches the constraint
     * violation and re-reads the winner's row.
     */
    private DemoWallet loadOrCreateWallet() {
        UUID userId = currentUserProvider.getCurrentUserId();
        return demoWalletRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
            try {
                return demoWalletRepository.saveAndFlush(DemoWallet.builder()
                    .user(user)
                    .balance(BigDecimal.ZERO)
                    .currency(DEFAULT_CURRENCY)
                    .build());
            } catch (DataIntegrityViolationException e) {
                return demoWalletRepository.findByUserId(userId)
                    .orElseThrow(() -> new ApiException("Could not open wallet", "WALLET_CREATE_FAILED"));
            }
        });
    }

    private WalletResponse toResponse(DemoWallet wallet) {
        return new WalletResponse(wallet.getId(), wallet.getBalance(), wallet.getCurrency());
    }
}
