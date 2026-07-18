package kh.edu.acleda.campuslink.modules.productivity.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByUserIdAndCompletedFalseAndDeletedAtIsNull(UUID userId);

    long countByUserIdAndDeletedAtIsNull(UUID userId);

    long countByUserIdAndDeletedAtIsNullAndCompletedTrue(UUID userId);

    Optional<Task> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    /** Incomplete first, then earliest due date (Postgres sorts NULL due dates last). */
    List<Task> findByUserIdAndDeletedAtIsNullOrderByCompletedAscDueDateAsc(UUID userId);
}
