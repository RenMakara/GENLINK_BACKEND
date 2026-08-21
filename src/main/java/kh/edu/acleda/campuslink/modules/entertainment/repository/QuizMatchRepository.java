package kh.edu.acleda.campuslink.modules.entertainment.repository;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizMatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizMatchRepository extends JpaRepository<QuizMatch, UUID> {
}
