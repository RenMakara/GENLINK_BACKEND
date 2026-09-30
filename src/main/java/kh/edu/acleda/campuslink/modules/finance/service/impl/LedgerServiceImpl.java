package kh.edu.acleda.campuslink.modules.finance.service.impl;

import java.math.BigDecimal;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoTransaction;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionDirection;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionStatus;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;
import kh.edu.acleda.campuslink.modules.finance.repository.DemoTransactionRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.DemoWalletRepository;
import kh.edu.acleda.campuslink.modules.finance.service.LedgerService;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerServiceImpl implements LedgerService {

    private final DemoTransactionRepository transactionRepository;
    private final DemoWalletRepository walletRepository;
    private final EntityManager entityManager;

    public LedgerServiceImpl(DemoTransactionRepository transactionRepository,
                             DemoWalletRepository walletRepository,
                             EntityManager entityManager) {
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
        this.entityManager = entityManager;
    }

    /** Credits the wallet balance and records a CREDIT transaction. */
    @Override
    @Transactional
    public DemoTransaction credit(DemoWallet wallet, TransactionType type, Currency currency, BigDecimal amount,
                                  String description, String relatedType, UUID relatedId) {
        DemoWallet lockedWallet = lockForMutation(wallet);
        setBalanceFor(lockedWallet, currency, balanceFor(lockedWallet, currency).add(amount));
        walletRepository.save(lockedWallet);
        copyBalances(lockedWallet, wallet);
        return record(lockedWallet, type, currency, TransactionDirection.CREDIT, amount,
            description, relatedType, relatedId);
    }

    /** Debits the wallet balance and records a DEBIT transaction. */
    @Override
    @Transactional
    public DemoTransaction debit(DemoWallet wallet, TransactionType type, Currency currency, BigDecimal amount,
                                 String description, String relatedType, UUID relatedId,
                                 String provider, String providerRef) {
        DemoWallet lockedWallet = lockForMutation(wallet);
        // Check the latest committed balance while holding the row lock.
        if (balanceFor(lockedWallet, currency).compareTo(amount) < 0) {
            throw new ApiException("Demo wallet balance is too low for this payment",
                "DEMO_WALLET_INSUFFICIENT_BALANCE");
        }
        setBalanceFor(lockedWallet, currency, balanceFor(lockedWallet, currency).subtract(amount));
        walletRepository.save(lockedWallet);
        copyBalances(lockedWallet, wallet);
        return record(lockedWallet, type, currency, TransactionDirection.DEBIT, amount, description,
            relatedType, relatedId, provider, providerRef);
    }

    /** Returns a fresh, write-locked wallet. */
    private DemoWallet lockForMutation(DemoWallet wallet) {
        if (wallet == null || wallet.getId() == null) {
            throw new ApiException("Wallet must be persisted before a balance mutation", "WALLET_NOT_PERSISTED");
        }
        if (entityManager.contains(wallet)) {
            // A currency change can perform two ledger mutations in one outer transaction.
            entityManager.flush();
            entityManager.refresh(wallet, LockModeType.PESSIMISTIC_WRITE);
            return wallet;
        }
        return walletRepository.findByIdForUpdate(wallet.getId())
            .orElseThrow(() -> new NotFoundException("Demo wallet not found"));
    }

    /** Keeps existing callers' response/log object synchronized when a detached wallet was passed. */
    private void copyBalances(DemoWallet source, DemoWallet target) {
        if (source != target) {
            target.setBalanceUsd(source.getBalanceUsd());
            target.setBalanceKhr(source.getBalanceKhr());
        }
    }

    /** Reads the wallet's balance for the given currency, treating null as zero. */
    private BigDecimal balanceFor(DemoWallet wallet, Currency currency) {
        BigDecimal balance = currency == Currency.KHR ? wallet.getBalanceKhr() : wallet.getBalanceUsd();
        return balance == null ? BigDecimal.ZERO : balance;
    }

    /** Writes the wallet's balance for the given currency. */
    private void setBalanceFor(DemoWallet wallet, Currency currency, BigDecimal value) {
        if (currency == Currency.KHR) {
            wallet.setBalanceKhr(value);
        } else {
            wallet.setBalanceUsd(value);
        }
    }

    @Override
    @Transactional
    public DemoTransaction record(DemoWallet wallet, TransactionType type, Currency currency,
                                  TransactionDirection direction, BigDecimal amount, String description,
                                  String relatedType, UUID relatedId, String provider, String providerRef) {
        return transactionRepository.save(DemoTransaction.builder()
            .wallet(wallet)
            // Store the owner for direct user-history queries.
            .user(wallet == null ? null : wallet.getUser())
            .transactionType(type)
            .currency(currency)
            .direction(direction)
            .amount(amount)
            .description(description)
            .relatedType(relatedType)
            .relatedId(relatedId)
            // Opaque provider metadata (null for non-payment rows) - stored, never interpreted here.
            .provider(provider)
            .providerRef(providerRef)
            .status(TransactionStatus.SUCCESS)
            .build());
    }
}
