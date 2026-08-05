package kh.edu.acleda.campuslink.modules.finance.service;

import java.math.BigDecimal;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoTransaction;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionDirection;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;

/**
 * Central hub for all financial transactions. Every money movement - top-ups, payments,
 * rewards, and future expenses/income/savings - flows through here and creates a single
 * {@link DemoTransaction} entry with info about what triggered it.
 *
 * <p>Provides two main operations: {@link #credit} to add money and {@link #debit} to
 * remove it (with a check to prevent overdrafts). Both record the transaction and update
 * the wallet balance. Think of this as the single point where the ledger and wallet stay
 * in sync.
 */
public interface LedgerService {

    /** Adds {@code amount} to the wallet's {@code currency} balance and records a CREDIT row. */
    DemoTransaction credit(DemoWallet wallet, TransactionType type, Currency currency, BigDecimal amount,
                           String description, String relatedType, UUID relatedId);

    /**
     * Removes {@code amount} from the wallet's {@code currency} balance and records a DEBIT row,
     * tagging it with the payment {@code provider}/{@code providerRef} (both null for non-payment
     * debits). Throws DEMO_WALLET_INSUFFICIENT_BALANCE, before any write, when the balance is too
     * low. Provider values are opaque here - the ledger stores them, it does not interpret them.
     */
    DemoTransaction debit(DemoWallet wallet, TransactionType type, Currency currency, BigDecimal amount,
                          String description, String relatedType, UUID relatedId,
                          String provider, String providerRef);

    /** Debit with no provider metadata (income/expense/saving/event - not a payment-network charge). */
    default DemoTransaction debit(DemoWallet wallet, TransactionType type, Currency currency, BigDecimal amount,
                                  String description, String relatedType, UUID relatedId) {
        return debit(wallet, type, currency, amount, description, relatedType, relatedId, null, null);
    }

    default DemoTransaction credit(DemoWallet wallet, TransactionType type, Currency currency,
                                   BigDecimal amount, String description) {
        return credit(wallet, type, currency, amount, description, null, null);
    }

    default DemoTransaction debit(DemoWallet wallet, TransactionType type, Currency currency,
                                  BigDecimal amount, String description) {
        return debit(wallet, type, currency, amount, description, null, null, null, null);
    }

    /**
     * Low-level: writes a ledger row with an explicit direction and optional provider metadata, and
     * does NOT move the balance. Prefer {@link #credit}/{@link #debit}; this exists for rows that
     * reconcile the balance by other means.
     */
    DemoTransaction record(DemoWallet wallet, TransactionType type, Currency currency,
                           TransactionDirection direction, BigDecimal amount, String description,
                           String relatedType, UUID relatedId, String provider, String providerRef);

    /** Record with no provider metadata. */
    default DemoTransaction record(DemoWallet wallet, TransactionType type, Currency currency,
                                   TransactionDirection direction, BigDecimal amount, String description,
                                   String relatedType, UUID relatedId) {
        return record(wallet, type, currency, direction, amount, description, relatedType, relatedId, null, null);
    }
}
