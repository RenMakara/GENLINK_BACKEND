package kh.edu.acleda.campuslink.modules.entertainment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.UserChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserChallengeRepository extends JpaRepository<UserChallenge, UUID> {

    Optional<UserChallenge> findByUserIdAndChallengeId(UUID userId, UUID challengeId);

    List<UserChallenge> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
