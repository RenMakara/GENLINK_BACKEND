package kh.edu.acleda.campuslink.modules.entertainment.repository;

import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizAttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface QuizAttemptAnswerRepository extends JpaRepository<QuizAttemptAnswer, UUID> {}
