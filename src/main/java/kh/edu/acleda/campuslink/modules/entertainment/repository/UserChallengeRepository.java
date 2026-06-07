package kh.edu.acleda.campuslink.modules.entertainment.repository;

import kh.edu.acleda.campuslink.modules.entertainment.entity.UserChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserChallengeRepository extends JpaRepository<UserChallenge, UUID> {}
