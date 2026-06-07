package kh.edu.acleda.campuslink.modules.productivity.repository;

import kh.edu.acleda.campuslink.modules.productivity.entity.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {}
