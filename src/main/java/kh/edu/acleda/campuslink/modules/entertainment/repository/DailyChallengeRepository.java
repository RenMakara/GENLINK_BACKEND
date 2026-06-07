package kh.edu.acleda.campuslink.modules.entertainment.repository;

import kh.edu.acleda.campuslink.modules.entertainment.entity.DailyChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DailyChallengeRepository extends JpaRepository<DailyChallenge, UUID> {}
