package kh.edu.acleda.campuslink.modules.finance.repository;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.entity.SavingDeposit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingDepositRepository extends JpaRepository<SavingDeposit, UUID> {

    /** Deposit history for a goal, most recent first. */
    List<SavingDeposit> findBySavingGoalIdOrderByDepositDateDescCreatedAtDesc(UUID savingGoalId);
}
