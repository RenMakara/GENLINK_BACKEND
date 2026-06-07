package kh.edu.acleda.campuslink.modules.productivity.repository;

import kh.edu.acleda.campuslink.modules.productivity.entity.HabitCheckIn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HabitCheckInRepository extends JpaRepository<HabitCheckIn, UUID> {}
