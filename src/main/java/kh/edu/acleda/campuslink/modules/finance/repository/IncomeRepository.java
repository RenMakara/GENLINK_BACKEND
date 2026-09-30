package kh.edu.acleda.campuslink.modules.finance.repository;

import kh.edu.acleda.campuslink.modules.finance.entity.Income;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface IncomeRepository extends JpaRepository<Income, UUID> {

    Optional<Income> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Income i WHERE i.id = :id AND i.user.id = :userId AND i.deletedAt IS NULL")
    Optional<Income> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    /** All of the user's income, optionally filtered by source. */
    @Query(value = "SELECT i FROM Income i WHERE i.user.id = :userId AND i.deletedAt IS NULL "
        + "AND (:source IS NULL OR i.source = :source) "
        + "AND (:currency IS NULL OR i.currency = :currency)",
        countQuery = "SELECT COUNT(i) FROM Income i WHERE i.user.id = :userId AND i.deletedAt IS NULL "
            + "AND (:source IS NULL OR i.source = :source) "
            + "AND (:currency IS NULL OR i.currency = :currency)")
    Page<Income> findByUser(@Param("userId") UUID userId, @Param("source") String source,
                            @Param("currency") Currency currency, Pageable pageable);

    /** Income within a half-open date range [start, end). */
    @Query(value = "SELECT i FROM Income i WHERE i.user.id = :userId AND i.deletedAt IS NULL "
        + "AND (:source IS NULL OR i.source = :source) "
        + "AND (:currency IS NULL OR i.currency = :currency) "
        + "AND i.incomeDate >= :start AND i.incomeDate < :end",
        countQuery = "SELECT COUNT(i) FROM Income i WHERE i.user.id = :userId AND i.deletedAt IS NULL "
            + "AND (:source IS NULL OR i.source = :source) "
            + "AND (:currency IS NULL OR i.currency = :currency) "
            + "AND i.incomeDate >= :start AND i.incomeDate < :end")
    Page<Income> findByUserAndDateRange(@Param("userId") UUID userId, @Param("source") String source,
                                        @Param("currency") Currency currency,
                                        @Param("start") LocalDate start, @Param("end") LocalDate end,
                                        Pageable pageable);

    /** Per-currency total income for a month - so cash-flow never mixes KHR with USD. */
    @Query("SELECT SUM(i.amount) FROM Income i WHERE i.user.id = :userId AND i.deletedAt IS NULL "
        + "AND i.currency = :currency AND i.incomeDate >= :start AND i.incomeDate < :end")
    BigDecimal sumForMonthByCurrency(@Param("userId") UUID userId, @Param("currency") Currency currency,
                                     @Param("start") LocalDate start, @Param("end") LocalDate end);
}
