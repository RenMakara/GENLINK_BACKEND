package kh.edu.acleda.campuslink.modules.finance.repository;

import kh.edu.acleda.campuslink.modules.finance.entity.BudgetCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BudgetCategoryRepository extends JpaRepository<BudgetCategory, UUID> {}
