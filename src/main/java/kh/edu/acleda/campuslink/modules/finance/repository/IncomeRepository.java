package kh.edu.acleda.campuslink.modules.finance.repository;

import kh.edu.acleda.campuslink.modules.finance.entity.Income;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface IncomeRepository extends JpaRepository<Income, UUID> {

    Optional<Income> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    /** All of the user's income, optionally filtered by source. */
    @Query(value = "SELECT i FROM Income i WHERE i.user.id = :userId AND i.deletedAt IS NULL "
        + "AND (:source IS NULL OR i.source = :source)",
        countQuery = "SELECT COUNT(i) FROM Income i WHERE i.user.id = :userId AND i.deletedAt IS NULL "
            + "AND (:source IS NULL OR i.source = :source)")
    Page<Income> findByUser(@Param("userId") UUID userId, @Param("source") String source, Pageable pageable);

    /**
     * Income within a half-open date range [start, end). Split from findByUser so dates only
     * appear in comparisons, never in an IS NULL check - a bare date param in IS NULL breaks
     * on PostgreSQL (could not determine data type). Same trap the expense repo avoids.
     */
    @Query(value = "SELECT i FROM Income i WHERE i.user.id = :userId AND i.deletedAt IS NULL "
        + "AND (:source IS NULL OR i.source = :source) "
        + "AND i.incomeDate >= :start AND i.incomeDate < :end",
        countQuery = "SELECT COUNT(i) FROM Income i WHERE i.user.id = :userId AND i.deletedAt IS NULL "
            + "AND (:source IS NULL OR i.source = :source) "
            + "AND i.incomeDate >= :start AND i.incomeDate < :end")
    Page<Income> findByUserAndDateRange(@Param("userId") UUID userId, @Param("source") String source,
                                        @Param("start") LocalDate start, @Param("end") LocalDate end,
                                        Pageable pageable);

    /** Total income for a month; null when there is none. */
    @Query("SELECT SUM(i.amount) FROM Income i WHERE i.user.id = :userId AND i.deletedAt IS NULL "
        + "AND i.incomeDate >= :start AND i.incomeDate < :end")
    BigDecimal sumForMonth(@Param("userId") UUID userId,
                           @Param("start") LocalDate start, @Param("end") LocalDate end);
}
