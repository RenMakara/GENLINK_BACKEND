package kh.edu.acleda.campuslink.modules.productivity.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.entity.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    Optional<Goal> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    List<Goal> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    /** The dashboard only exposes the five most recently created goals. */
    List<Goal> findTop5ByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);
}
