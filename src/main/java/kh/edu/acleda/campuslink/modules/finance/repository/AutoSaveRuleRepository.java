package kh.edu.acleda.campuslink.modules.finance.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.entity.AutoSaveRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AutoSaveRuleRepository extends JpaRepository<AutoSaveRule, UUID> {

    Optional<AutoSaveRule> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    List<AutoSaveRule> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    /** Active rules due to run - the scheduler's work list. */
    @Query("SELECT a FROM AutoSaveRule a WHERE a.active = true AND a.deletedAt IS NULL "
        + "AND a.nextRunDate <= :asOf")
    List<AutoSaveRule> findDue(@Param("asOf") LocalDate asOf);
}
