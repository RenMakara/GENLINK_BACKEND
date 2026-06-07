package kh.edu.acleda.campuslink.modules.finance.repository;

import kh.edu.acleda.campuslink.modules.finance.entity.SavingGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SavingGoalRepository extends JpaRepository<SavingGoal, UUID> {}
