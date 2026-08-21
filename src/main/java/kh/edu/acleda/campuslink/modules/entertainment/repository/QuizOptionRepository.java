package kh.edu.acleda.campuslink.modules.entertainment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizOption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizOptionRepository extends JpaRepository<QuizOption, UUID> {

    List<QuizOption> findByQuestionIdOrderByCreatedAtAsc(UUID questionId);

    Optional<QuizOption> findByIdAndQuestionId(UUID id, UUID questionId);
}
