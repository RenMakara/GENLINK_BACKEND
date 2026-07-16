package kh.edu.acleda.campuslink.modules.finance.repository;

import java.util.List;
import kh.edu.acleda.campuslink.modules.finance.enums.SavingGoalStatus;
import kh.edu.acleda.campuslink.modules.finance.entity.SavingGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SavingGoalRepository extends JpaRepository<SavingGoal, UUID> {

    List<SavingGoal> findByUserIdAndDeletedAtIsNullAndStatus(UUID userId, SavingGoalStatus status);
}
