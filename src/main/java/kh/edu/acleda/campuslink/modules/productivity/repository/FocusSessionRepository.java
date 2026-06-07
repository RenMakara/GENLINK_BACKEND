package kh.edu.acleda.campuslink.modules.productivity.repository;

import kh.edu.acleda.campuslink.modules.productivity.entity.FocusSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FocusSessionRepository extends JpaRepository<FocusSession, UUID> {}
