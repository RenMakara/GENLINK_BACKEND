package kh.edu.acleda.campuslink.modules.entertainment.repository;

import kh.edu.acleda.campuslink.modules.entertainment.entity.Leaderboard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LeaderboardRepository extends JpaRepository<Leaderboard, UUID> {}
