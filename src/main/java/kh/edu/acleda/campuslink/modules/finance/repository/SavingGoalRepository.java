package kh.edu.acleda.campuslink.modules.finance.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import kh.edu.acleda.campuslink.modules.finance.entity.SavingGoal;
import kh.edu.acleda.campuslink.modules.finance.enums.SavingGoalStatus;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavingGoalRepository extends JpaRepository<SavingGoal, UUID> {

    Optional<SavingGoal> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM SavingGoal g WHERE g.id = :id AND g.user.id = :userId AND g.deletedAt IS NULL")
    Optional<SavingGoal> findByIdAndUserIdAndDeletedAtIsNullForUpdate(
        @Param("id") UUID id, @Param("userId") UUID userId);

    List<SavingGoal> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    List<SavingGoal> findByUserIdAndDeletedAtIsNullAndStatusOrderByCreatedAtDesc(
        UUID userId, SavingGoalStatus status);

    @Query("SELECT g FROM SavingGoal g WHERE g.user.id = :userId AND g.deletedAt IS NULL "
        + "AND (:status IS NULL OR g.status = :status) "
        + "AND (:currency IS NULL OR g.currency = :currency) ORDER BY g.createdAt DESC")
    List<SavingGoal> findByUser(@Param("userId") UUID userId,
                                @Param("status") SavingGoalStatus status,
                                @Param("currency") Currency currency);
}
