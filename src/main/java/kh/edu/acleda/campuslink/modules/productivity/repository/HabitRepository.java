package kh.edu.acleda.campuslink.modules.productivity.repository;

import kh.edu.acleda.campuslink.modules.productivity.entity.Habit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HabitRepository extends JpaRepository<Habit, UUID> {

    long countByUserIdAndDeletedAtIsNull(UUID userId);
}
