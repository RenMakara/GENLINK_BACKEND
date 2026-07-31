package kh.edu.acleda.campuslink.modules.finance.repository;

import kh.edu.acleda.campuslink.modules.finance.entity.Expense;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    Optional<Expense> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    /** All of the user's expenses, optionally filtered by category. */
    @Query(value = "SELECT e FROM Expense e WHERE e.user.id = :userId AND e.deletedAt IS NULL "
        + "AND (:category IS NULL OR e.category = :category)",
        countQuery = "SELECT COUNT(e) FROM Expense e WHERE e.user.id = :userId AND e.deletedAt IS NULL "
            + "AND (:category IS NULL OR e.category = :category)")
    Page<Expense> findByUser(@Param("userId") UUID userId, @Param("category") String category, Pageable pageable);

    /**
     * Expenses within a half-open date range [start, end). Split from findByUser so the
     * dates only appear in comparisons, never in an `IS NULL` check - a bare date param
     * in IS NULL breaks on PostgreSQL (could not determine data type).
     */
    @Query(value = "SELECT e FROM Expense e WHERE e.user.id = :userId AND e.deletedAt IS NULL "
        + "AND (:category IS NULL OR e.category = :category) "
        + "AND e.expenseDate >= :start AND e.expenseDate < :end",
        countQuery = "SELECT COUNT(e) FROM Expense e WHERE e.user.id = :userId AND e.deletedAt IS NULL "
            + "AND (:category IS NULL OR e.category = :category) "
            + "AND e.expenseDate >= :start AND e.expenseDate < :end")
    Page<Expense> findByUserAndDateRange(@Param("userId") UUID userId, @Param("category") String category,
                                         @Param("start") LocalDate start, @Param("end") LocalDate end,
                                         Pageable pageable);

    /** Per-category totals for a month, biggest first - backs the spending chart. */
    @Query("SELECT e.category, SUM(e.amount) FROM Expense e "
        + "WHERE e.user.id = :userId AND e.deletedAt IS NULL "
        + "AND e.expenseDate >= :start AND e.expenseDate < :end "
        + "GROUP BY e.category ORDER BY SUM(e.amount) DESC")
    List<Object[]> sumByCategory(@Param("userId") UUID userId,
                                 @Param("start") LocalDate start, @Param("end") LocalDate end);

    /** Total spend for a month; null when there are no expenses. */
    @Query("SELECT SUM(e.amount) FROM Expense e WHERE e.user.id = :userId AND e.deletedAt IS NULL "
        + "AND e.expenseDate >= :start AND e.expenseDate < :end")
    BigDecimal sumForMonth(@Param("userId") UUID userId,
                           @Param("start") LocalDate start, @Param("end") LocalDate end);

    /** Per-currency total spend for a month - so a budget/cash-flow never mixes KHR with USD. */
    @Query("SELECT SUM(e.amount) FROM Expense e WHERE e.user.id = :userId AND e.deletedAt IS NULL "
        + "AND e.currency = :currency AND e.expenseDate >= :start AND e.expenseDate < :end")
    BigDecimal sumForMonthByCurrency(@Param("userId") UUID userId, @Param("currency") Currency currency,
                                     @Param("start") LocalDate start, @Param("end") LocalDate end);

    /** Raw expense amounts for a month in one currency - backs round-up auto-save. */
    @Query("SELECT e.amount FROM Expense e WHERE e.user.id = :userId AND e.deletedAt IS NULL "
        + "AND e.currency = :currency AND e.expenseDate >= :start AND e.expenseDate < :end")
    List<BigDecimal> findAmountsForMonthByCurrency(@Param("userId") UUID userId, @Param("currency") Currency currency,
                                                   @Param("start") LocalDate start, @Param("end") LocalDate end);

    /** Per-currency, per-category totals for a month, biggest first. */
    @Query("SELECT e.category, SUM(e.amount) FROM Expense e "
        + "WHERE e.user.id = :userId AND e.deletedAt IS NULL AND e.currency = :currency "
        + "AND e.expenseDate >= :start AND e.expenseDate < :end "
        + "GROUP BY e.category ORDER BY SUM(e.amount) DESC")
    List<Object[]> sumByCategoryAndCurrency(@Param("userId") UUID userId, @Param("currency") Currency currency,
                                            @Param("start") LocalDate start, @Param("end") LocalDate end);
}
