package kh.edu.acleda.campuslink.modules.entertainment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    Optional<Quiz> findByIdAndDeletedAtIsNull(UUID id);

    List<Quiz> findByDeletedAtIsNullOrderByCreatedAtDesc();
}
