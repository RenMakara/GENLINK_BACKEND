package kh.edu.acleda.campuslink.modules.finance.repository;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DemoTransactionRepository extends JpaRepository<DemoTransaction, UUID> {

    /** Wallet history, most recent first. */
    Page<DemoTransaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);
}
