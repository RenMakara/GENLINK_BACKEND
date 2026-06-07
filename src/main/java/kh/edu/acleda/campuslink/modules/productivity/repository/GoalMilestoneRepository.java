package kh.edu.acleda.campuslink.modules.productivity.repository;

import kh.edu.acleda.campuslink.modules.productivity.entity.GoalMilestone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GoalMilestoneRepository extends JpaRepository<GoalMilestone, UUID> {}
