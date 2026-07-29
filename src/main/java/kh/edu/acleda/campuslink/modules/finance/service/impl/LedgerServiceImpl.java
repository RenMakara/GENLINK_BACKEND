package kh.edu.acleda.campuslink.modules.finance.service.impl;

import java.math.BigDecimal;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoTransaction;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionDirection;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionStatus;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;
import kh.edu.acleda.campuslink.modules.finance.repository.DemoTransactionRepository;
import kh.edu.acleda.campuslink.modules.finance.service.LedgerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerServiceImpl implements LedgerService {

    private final DemoTransactionRepository transactionRepository;

    public LedgerServiceImpl(DemoTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional
    public DemoTransaction record(DemoWallet wallet, TransactionType type, TransactionDirection direction,
                                  BigDecimal amount, String description, String relatedType, UUID relatedId) {
        return transactionRepository.save(DemoTransaction.builder()
            .wallet(wallet)
            // The wallet always carries its owner; denormalising it onto the row keeps a
            // user's whole history to one query and survives if wallets ever go multi-currency.
            .user(wallet == null ? null : wallet.getUser())
            .transactionType(type)
            .direction(direction)
            .amount(amount)
            .description(description)
            .relatedType(relatedType)
            .relatedId(relatedId)
            .status(TransactionStatus.SUCCESS)
            .build());
    }
}
