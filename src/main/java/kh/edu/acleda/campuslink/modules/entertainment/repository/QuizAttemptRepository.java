package kh.edu.acleda.campuslink.modules.entertainment.repository;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {

    boolean existsByUserIdAndCompletedAtIsNotNullAndCreatedAtAfter(UUID userId, java.time.Instant after);

    /** True once the user has already earned XP on this quiz - XP is once per quiz. */
    boolean existsByQuizIdAndUserIdAndXpEarnedGreaterThan(UUID quizId, UUID userId, int xpEarned);

    List<QuizAttempt> findByUserIdOrderByCompletedAtDesc(UUID userId);
}
