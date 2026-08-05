package kh.edu.acleda.campuslink.modules.finance.service.impl;

import java.math.BigDecimal;
import java.util.UUID;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerServiceImpl implements LedgerService {

    private final DemoTransactionRepository transactionRepository;
    private final DemoWalletRepository walletRepository;

    public LedgerServiceImpl(DemoTransactionRepository transactionRepository,
                             DemoWalletRepository walletRepository) {
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
    }

    /**
     * Method នេះប្រើសម្រាប់ Credit amount ចូលទៅក្នុង Wallet Balance តាម Currency
     * និងបង្កើត CREDIT Transaction នៅក្នុង Finance Ledger
     *
     * <p>វាជា single entry point សម្រាប់ Wallet inflows ទាំងអស់
     * (ឧ. Income, Top-up និង Reward)។ Wallet Balance និង Ledger Transaction
     * នឹងត្រូវបាន persist ក្នុង Transaction តែមួយ ដើម្បីធានា data consistency</p>
     *
     * @param wallet target wallet
     * @param type transaction type
     * @param currency transaction currency
     * @param amount amount to credit
     * @param description transaction description
     * @param relatedType source entity type
     * @param relatedId source entity identifier
     * @return persisted CREDIT transaction
     *
     * @author RenMakara
     */
    @Override
    @Transactional
    public DemoTransaction credit(DemoWallet wallet, TransactionType type, Currency currency, BigDecimal amount,
                                  String description, String relatedType, UUID relatedId) {
        setBalanceFor(wallet, currency, balanceFor(wallet, currency).add(amount));
        walletRepository.save(wallet);
        return record(wallet, type, currency, TransactionDirection.CREDIT, amount, description, relatedType, relatedId);
    }

    /**
     * Method នេះប្រើសម្រាប់ Debit amount ចេញពី Wallet Balance តាម Currency
     * និងបង្កើត DEBIT Transaction នៅក្នុង Finance Ledger។
     *
     * <p>វាជា single entry point សម្រាប់ Wallet outflows ទាំងអស់
     * (ឧ. Expense, Saving Deposit និង Event Payment)។ ប្រព័ន្ធនឹង validate
     * Wallet Balance មុនពេលធ្វើ write operation ណាមួយ។</p>
     *
     * <p>ប្រសិនបើ Wallet Balance មិនគ្រប់គ្រាន់ ApiException នឹងត្រូវបាន throw
     * ហើយ Transaction ទាំងមូលនឹង rollback ដោយមិនមាន partial state កើតឡើងឡើយ។
     * Wallet Balance update និង Ledger Transaction persistence នឹងត្រូវបាន
     * execute ក្នុង Transaction តែមួយ ដើម្បីធានា atomicity និង consistency។</p>
     *
     * @param wallet target wallet
     * @param type transaction type
     * @param currency transaction currency
     * @param amount amount to debit
     * @param description transaction description
     * @param relatedType source entity type
     * @param relatedId source entity identifier
     * @return persisted DEBIT transaction
     * @throws ApiException if the wallet balance is insufficient
     *
     * @author RenMakara
     */
    @Override
    @Transactional
    public DemoTransaction debit(DemoWallet wallet, TransactionType type, Currency currency, BigDecimal amount,
                                 String description, String relatedType, UUID relatedId,
                                 String provider, String providerRef) {
        // The single insufficient-funds guard: thrown before any write, so no partial state.
        if (balanceFor(wallet, currency).compareTo(amount) < 0) {
            throw new ApiException("Demo wallet balance is too low for this payment",
                "DEMO_WALLET_INSUFFICIENT_BALANCE");
        }
        setBalanceFor(wallet, currency, balanceFor(wallet, currency).subtract(amount));
        walletRepository.save(wallet);
        return record(wallet, type, currency, TransactionDirection.DEBIT, amount, description,
            relatedType, relatedId, provider, providerRef);
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
            // The wallet always carries its owner; denormalising it onto the row keeps a
            // user's whole history to one query and matches the now multi-currency wallet.
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
