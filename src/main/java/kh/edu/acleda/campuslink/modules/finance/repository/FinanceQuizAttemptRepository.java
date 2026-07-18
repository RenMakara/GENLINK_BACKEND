package kh.edu.acleda.campuslink.modules.finance.repository;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.entity.FinanceQuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceQuizAttemptRepository extends JpaRepository<FinanceQuizAttempt, UUID> {
}
