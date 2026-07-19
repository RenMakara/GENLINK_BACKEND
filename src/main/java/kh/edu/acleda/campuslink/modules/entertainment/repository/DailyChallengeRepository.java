package kh.edu.acleda.campuslink.modules.entertainment.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.DailyChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyChallengeRepository extends JpaRepository<DailyChallenge, UUID> {

    List<DailyChallenge> findByDeletedAtIsNullAndDeadlineAfterOrderByDeadlineAsc(LocalDateTime now);

    Optional<DailyChallenge> findByIdAndDeletedAtIsNull(UUID id);

    List<DailyChallenge> findByDeletedAtIsNullOrderByCreatedAtDesc();
}
