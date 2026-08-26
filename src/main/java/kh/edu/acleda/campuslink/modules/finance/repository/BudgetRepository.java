package kh.edu.acleda.campuslink.modules.finance.repository;

import kh.edu.acleda.campuslink.modules.finance.entity.Budget;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    Optional<Budget> findByUserIdAndMonthAndYearAndCurrencyAndDeletedAtIsNull(
        UUID userId, Integer month, Integer year, Currency currency);

    Optional<Budget> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    Page<Budget> findByUserIdAndDeletedAtIsNull(UUID userId, Pageable pageable);

    boolean existsByUserIdAndMonthAndYearAndCurrencyAndDeletedAtIsNull(
        UUID userId, Integer month, Integer year, Currency currency);

    boolean existsByUserIdAndMonthAndYearAndCurrencyAndDeletedAtIsNullAndIdNot(
        UUID userId, Integer month, Integer year, Currency currency, UUID id);
}
