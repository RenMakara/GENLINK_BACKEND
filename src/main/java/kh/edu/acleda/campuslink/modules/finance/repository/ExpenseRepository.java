package kh.edu.acleda.campuslink.modules.finance.repository;

import kh.edu.acleda.campuslink.modules.finance.entity.Expense;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {
    List<Expense> findByUserIdAndDeletedAtIsNull(UUID userId);
}
