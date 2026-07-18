package kh.edu.acleda.campuslink.modules.productivity.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.entity.GoalMilestone;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalMilestoneRepository extends JpaRepository<GoalMilestone, UUID> {

    List<GoalMilestone> findByGoalIdOrderByCreatedAtAsc(UUID goalId);

    Optional<GoalMilestone> findByIdAndGoalId(UUID id, UUID goalId);
}
