package kh.edu.acleda.campuslink.modules.finance.repository;

import kh.edu.acleda.campuslink.modules.finance.entity.BudgetCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BudgetCategoryRepository extends JpaRepository<BudgetCategory, UUID> {

    List<BudgetCategory> findByBudgetId(UUID budgetId);

    void deleteByBudgetId(UUID budgetId);
}
