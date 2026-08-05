package kh.edu.acleda.campuslink.modules.finance.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.entity.RecurringTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, UUID> {

    Optional<RecurringTransaction> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    List<RecurringTransaction> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    /** Active templates whose next run has arrived - the scheduler's work list. */
    @Query("SELECT r FROM RecurringTransaction r WHERE r.active = true AND r.deletedAt IS NULL "
        + "AND r.nextRunDate <= :asOf")
    List<RecurringTransaction> findDue(@Param("asOf") LocalDate asOf);
}
