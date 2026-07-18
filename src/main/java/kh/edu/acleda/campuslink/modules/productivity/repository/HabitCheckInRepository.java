package kh.edu.acleda.campuslink.modules.productivity.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.entity.HabitCheckIn;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitCheckInRepository extends JpaRepository<HabitCheckIn, UUID> {

    long countByUserIdAndCheckInDateAndCompletedTrue(UUID userId, LocalDate checkInDate);

    Optional<HabitCheckIn> findByHabitIdAndUserIdAndCheckInDate(UUID habitId, UUID userId, LocalDate checkInDate);

    /** All check-ins for a habit, newest first - used to compute streak and rate. */
    List<HabitCheckIn> findByHabitIdAndUserIdOrderByCheckInDateDesc(UUID habitId, UUID userId);

    /** The user's completed check-ins for a day - backs the dashboard's per-habit flag. */
    List<HabitCheckIn> findByUserIdAndCheckInDateAndCompletedTrue(UUID userId, LocalDate checkInDate);
}
