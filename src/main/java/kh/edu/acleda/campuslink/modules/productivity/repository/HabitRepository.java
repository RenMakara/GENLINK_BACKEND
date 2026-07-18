package kh.edu.acleda.campuslink.modules.productivity.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.entity.Habit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitRepository extends JpaRepository<Habit, UUID> {

    long countByUserIdAndDeletedAtIsNull(UUID userId);

    Optional<Habit> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    List<Habit> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);
}
