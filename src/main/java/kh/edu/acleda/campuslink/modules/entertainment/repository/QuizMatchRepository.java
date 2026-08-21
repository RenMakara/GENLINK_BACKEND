package kh.edu.acleda.campuslink.modules.entertainment.repository;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizMatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuizMatchRepository extends JpaRepository<QuizMatch, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM QuizMatch m WHERE m.id = :id")
    Optional<QuizMatch> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM QuizMatch m "
        + "WHERE m.id = :matchId AND (m.player1.id = :userId OR m.player2.id = :userId)")
    boolean isParticipant(@Param("matchId") UUID matchId, @Param("userId") UUID userId);
}
