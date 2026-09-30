package kh.edu.acleda.campuslink.modules.finance.service;

import java.math.BigDecimal;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoTransaction;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionDirection;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;

/** Central hub for all financial transactions. */
public interface LedgerService {

    /** Adds {@code amount} to the wallet's {@code currency} balance and records a CREDIT row. */
    DemoTransaction credit(DemoWallet wallet, TransactionType type, Currency currency, BigDecimal amount,
                           String description, String relatedType, UUID relatedId);

    /** Removes {@code amount} from the wallet's {@code currency} balance. */
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

    /** Low-level: writes a ledger row with an explicit direction and optional provider metadata, and does NOT move the balance. */
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
