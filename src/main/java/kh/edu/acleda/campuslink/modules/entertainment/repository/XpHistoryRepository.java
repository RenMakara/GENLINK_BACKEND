package kh.edu.acleda.campuslink.modules.entertainment.repository;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.XpHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface XpHistoryRepository extends JpaRepository<XpHistory, UUID> {

    /** How many times the user earned XP from a given source - backs badge thresholds. */
    long countByUserIdAndSourceType(UUID userId, String sourceType);
}
