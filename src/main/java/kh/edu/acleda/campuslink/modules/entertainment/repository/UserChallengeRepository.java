package kh.edu.acleda.campuslink.modules.entertainment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.UserChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface UserChallengeRepository extends JpaRepository<UserChallenge, UUID> {

    Optional<UserChallenge> findByUserIdAndChallengeId(UUID userId, UUID challengeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT uc FROM UserChallenge uc WHERE uc.user.id = :userId AND uc.challenge.id = :challengeId")
    Optional<UserChallenge> findByUserIdAndChallengeIdForUpdate(
        @Param("userId") UUID userId, @Param("challengeId") UUID challengeId);

    List<UserChallenge> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
