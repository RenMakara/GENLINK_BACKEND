package kh.edu.acleda.campuslink.modules.finance.repository;

import kh.edu.acleda.campuslink.modules.finance.entity.FinancialQuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FinancialQuizQuestionRepository extends JpaRepository<FinancialQuizQuestion, UUID> {}
