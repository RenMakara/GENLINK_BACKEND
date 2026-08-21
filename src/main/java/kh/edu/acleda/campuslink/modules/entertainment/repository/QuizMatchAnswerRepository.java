package kh.edu.acleda.campuslink.modules.entertainment.repository;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizMatchAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizMatchAnswerRepository extends JpaRepository<QuizMatchAnswer, UUID> {
    boolean existsByMatchIdAndPlayerIdAndQuestionId(UUID matchId, UUID playerId, UUID questionId);
}
