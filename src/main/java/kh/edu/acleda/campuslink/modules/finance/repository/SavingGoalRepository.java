package kh.edu.acleda.campuslink.modules.finance.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.entity.SavingGoal;
import kh.edu.acleda.campuslink.modules.finance.enums.SavingGoalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingGoalRepository extends JpaRepository<SavingGoal, UUID> {

    Optional<SavingGoal> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    List<SavingGoal> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    List<SavingGoal> findByUserIdAndDeletedAtIsNullAndStatusOrderByCreatedAtDesc(
        UUID userId, SavingGoalStatus status);
}
