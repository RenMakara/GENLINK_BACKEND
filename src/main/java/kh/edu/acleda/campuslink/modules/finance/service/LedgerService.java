package kh.edu.acleda.campuslink.modules.finance.service;

import java.math.BigDecimal;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoTransaction;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionDirection;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;

/**
 * The single write path for the finance ledger. Every money event - top-up, payment, reward,
 * and (from later phases) expense, income and saving - records exactly one {@link
 * DemoTransaction} through here, tagged with its direction and a link back to what caused it.
 *
 * <p>Phase 0 scope: this owns writing the ledger row (direction, owner, related link, status);
 * balance movement still lives in each caller. Phase 2 promotes this to own balance movement
 * too, so expenses and savings can debit the wallet through the same path.
 */
public interface LedgerService {

    DemoTransaction record(DemoWallet wallet, TransactionType type, TransactionDirection direction,
                           BigDecimal amount, String description, String relatedType, UUID relatedId);

    /** Convenience for wallet-native rows that have no source entity to link back to. */
    default DemoTransaction record(DemoWallet wallet, TransactionType type, TransactionDirection direction,
                                   BigDecimal amount, String description) {
        return record(wallet, type, direction, amount, description, null, null);
    }
}
