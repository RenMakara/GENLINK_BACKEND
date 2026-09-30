package kh.edu.acleda.campuslink.modules.finance.service.impl;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.WalletPaymentRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.request.WalletTopUpRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.TransactionResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.WalletResponse;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoTransaction;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;
import kh.edu.acleda.campuslink.modules.finance.payment.PaymentInstruction;
import kh.edu.acleda.campuslink.modules.finance.payment.PaymentProvider;
import kh.edu.acleda.campuslink.modules.finance.payment.PaymentResult;
import kh.edu.acleda.campuslink.modules.finance.repository.DemoTransactionRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.DemoWalletRepository;
import kh.edu.acleda.campuslink.modules.finance.service.DemoWalletService;
import kh.edu.acleda.campuslink.modules.finance.service.LedgerService;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoWalletServiceImpl implements DemoWalletService {

    private static final Logger log = LoggerFactory.getLogger(DemoWalletServiceImpl.class);

    /** Keep the API backward-compatible: omitted wallet currency means USD. */
    private static final Currency DEFAULT_CURRENCY = Currency.USD;
    private static final String PAYMENT_RECEIPT_TEMPLATE = "FINANCE_WALLET_PAYMENT_RECEIPT";
    private static final String PAYMENT_RECEIPT_TYPE = "FINANCE_WALLET_PAYMENT";

    private final DemoWalletRepository demoWalletRepository;
    private final DemoTransactionRepository demoTransactionRepository;
    private final LedgerService ledgerService;
    private final PaymentProvider paymentProvider;
    private final NotificationEmitter notificationEmitter;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public DemoWalletServiceImpl(DemoWalletRepository demoWalletRepository,
                                 DemoTransactionRepository demoTransactionRepository,
                                 LedgerService ledgerService,
                                 PaymentProvider paymentProvider,
                                 NotificationEmitter notificationEmitter,
                                 UserRepository userRepository,
                                 CurrentUserProvider currentUserProvider) {
        this.demoWalletRepository = demoWalletRepository;
        this.demoTransactionRepository = demoTransactionRepository;
        this.ledgerService = ledgerService;
        this.paymentProvider = paymentProvider;
        this.notificationEmitter = notificationEmitter;
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
    public DemoWallet getOrCreateWalletEntity() {
        return loadOrCreateWallet();
    }

    @Override
    @Transactional
    public DemoWallet getOrCreateWalletEntity(User user) {
        // Background work (the recurring/auto-save scheduler) already holds the User.
        return demoWalletRepository.findByUserId(user.getId())
            .orElseGet(() -> loadOrCreateWalletForUserId(user.getId()));
    }

    @Override
    @Transactional
    public WalletResponse topUp(WalletTopUpRequest request) {
        DemoWallet wallet = loadOrCreateWallet();
        Currency currency = request.currency() == null ? DEFAULT_CURRENCY : request.currency();
        // credit adds the balance, saves the wallet and records the CREDIT row in one step.
        ledgerService.credit(wallet, TransactionType.DEPOSIT, currency, request.amount(),
            request.note() == null ? "Simulated top-up" : "Simulated top-up: " + request.note());
        log.info("SIMULATED wallet top-up: user={} currency={} amount={} balanceAfter={}",
            wallet.getUser().getId(), currency, request.amount(), balanceFor(wallet, currency));
        return toResponse(wallet);
    }

    @Override
    @Transactional
    public WalletResponse pay(WalletPaymentRequest request) {
        DemoWallet wallet = loadOrCreateWallet();
        Currency currency = request.currency() == null ? DEFAULT_CURRENCY : request.currency();
        String merchant = request.merchant() == null ? "merchant" : request.merchant();

        // 1. Ask the payment provider to authorize the charge.
        PaymentResult result = paymentProvider.charge(
            new PaymentInstruction(request.amount(), currency, merchant, request.note()));
        if (!result.isSuccess()) {
            throw new ApiException(
                "Payment was declined by the provider"
                    + (result.failureReason() == null ? "" : ": " + result.failureReason()),
                "PAYMENT_DECLINED");
        }

        // 2. Provider approved -> the ledger is the single source of truth.
        String description = "KHQR payment to " + merchant
            + (request.note() == null ? "" : " (" + request.note() + ")");
        DemoTransaction transaction = ledgerService.debit(wallet, TransactionType.KHQR_PAYMENT, currency,
            request.amount(), description,
            null, null, result.provider(), result.providerRef());
        emitPaymentReceiptNotification(wallet, transaction, request.amount(), currency, merchant, result);

        log.info("Wallet payment via {}: user={} currency={} amount={} merchant={} ref={} balanceAfter={}",
            result.provider(), wallet.getUser().getId(), currency, request.amount(), merchant,
            result.providerRef(), balanceFor(wallet, currency));
        return toResponse(wallet);
    }

    private void emitPaymentReceiptNotification(DemoWallet wallet, DemoTransaction transaction,
                                                BigDecimal amount, Currency currency, String merchant,
                                                PaymentResult result) {
        if (transaction == null || transaction.getId() == null || wallet == null || wallet.getUser() == null
            || wallet.getUser().getId() == null || currency == null) {
            log.warn("Skipping wallet payment receipt because payment details are incomplete: transaction={}",
                transaction == null ? null : transaction.getId());
            return;
        }

        String provider = result.provider() == null || result.provider().isBlank()
            ? "UNKNOWN" : result.provider();
        String providerRef = result.providerRef() == null || result.providerRef().isBlank()
            ? "UNAVAILABLE" : result.providerRef();
        notificationEmitter.emit(new NotificationRequest(
            wallet.getUser().getId(),
            PAYMENT_RECEIPT_TEMPLATE,
            PAYMENT_RECEIPT_TYPE,
            Map.of(
                "amount", formatAmount(amount),
                "currency", currency.name(),
                "merchant", merchant == null || merchant.isBlank() ? "merchant" : merchant,
                "provider", provider,
                "providerRef", providerRef),
            "TRANSACTION",
            transaction.getId(),
            "wallet-payment-receipt:" + transaction.getId()));
    }

    @Override
    @Transactional
    public WalletResponse creditReward(BigDecimal amount, String reason) {
        DemoWallet wallet = loadOrCreateWallet();
        ledgerService.credit(wallet, TransactionType.REWARD, DEFAULT_CURRENCY, amount, reason);
        log.info("SIMULATED wallet reward: user={} currency={} amount={} reason={} balanceAfter={}",
            wallet.getUser().getId(), DEFAULT_CURRENCY, amount, reason, balanceFor(wallet, DEFAULT_CURRENCY));
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
            t -> new TransactionResponse(t.getId(), t.getTransactionType(), t.getDirection(), t.getCurrency(),
                t.getAmount(), t.getDescription(), t.getStatus(), t.getCreatedAt(),
                t.getProvider(), t.getProviderRef()));
    }

    /** Get-or-create the current user's wallet. */
    private DemoWallet loadOrCreateWallet() {
        UUID userId = currentUserProvider.getCurrentUserId();
        return demoWalletRepository.findByUserId(userId)
            .orElseGet(() -> loadOrCreateWalletForUserId(userId));
    }

    /** Serialises wallet creation for one user. */
    private DemoWallet loadOrCreateWalletForUserId(UUID userId) {
        User user = userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));
        return demoWalletRepository.findByUserId(userId).orElseGet(() -> createWalletFor(user));
    }

    private DemoWallet createWalletFor(User user) {
        return demoWalletRepository.saveAndFlush(DemoWallet.builder()
            .user(user)
            .balanceUsd(BigDecimal.ZERO)
            .balanceKhr(BigDecimal.ZERO)
            .build());
    }

    private WalletResponse toResponse(DemoWallet wallet) {
        return new WalletResponse(wallet.getId(), wallet.getBalanceUsd(), wallet.getBalanceKhr());
    }

    private BigDecimal balanceFor(DemoWallet wallet, Currency currency) {
        return currency == Currency.KHR ? wallet.getBalanceKhr() : wallet.getBalanceUsd();
    }

    private String formatAmount(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }
}
