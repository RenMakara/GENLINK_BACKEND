package kh.edu.acleda.campuslink.modules.productivity.repository;

import kh.edu.acleda.campuslink.modules.productivity.entity.Task;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByUserIdAndCompletedFalseAndDeletedAtIsNull(UUID userId);
}
