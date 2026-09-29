package kh.edu.acleda.campuslink.modules.productivity.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.entity.Habit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface HabitRepository extends JpaRepository<Habit, UUID> {

    long countByUserIdAndDeletedAtIsNull(UUID userId);

    Optional<Habit> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM Habit h WHERE h.id = :id AND h.user.id = :userId AND h.deletedAt IS NULL")
    Optional<Habit> findByIdAndUserIdAndDeletedAtIsNullForUpdate(
        @Param("id") UUID id, @Param("userId") UUID userId);

    List<Habit> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);
}
