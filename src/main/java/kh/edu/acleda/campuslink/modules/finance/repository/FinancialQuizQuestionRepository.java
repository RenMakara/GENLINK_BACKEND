package kh.edu.acleda.campuslink.modules.finance.repository;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.entity.FinancialQuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialQuizQuestionRepository extends JpaRepository<FinancialQuizQuestion, UUID> {

    List<FinancialQuizQuestion> findByLessonIdOrderByCreatedAtAsc(UUID lessonId);
}
