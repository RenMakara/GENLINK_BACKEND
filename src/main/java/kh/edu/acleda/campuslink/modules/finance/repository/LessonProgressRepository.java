package kh.edu.acleda.campuslink.modules.finance.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.entity.LessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, UUID> {

    Optional<LessonProgress> findByUserIdAndLessonId(UUID userId, UUID lessonId);

    List<LessonProgress> findByUserId(UUID userId);
}
