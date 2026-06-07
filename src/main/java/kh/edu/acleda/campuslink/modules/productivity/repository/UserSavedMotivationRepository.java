package kh.edu.acleda.campuslink.modules.productivity.repository;

import kh.edu.acleda.campuslink.modules.productivity.entity.UserSavedMotivation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserSavedMotivationRepository extends JpaRepository<UserSavedMotivation, UUID> {}
