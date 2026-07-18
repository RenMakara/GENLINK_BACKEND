package kh.edu.acleda.campuslink.modules.productivity.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.entity.FocusSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FocusSessionRepository extends JpaRepository<FocusSession, UUID> {

    Optional<FocusSession> findByIdAndUserId(UUID id, UUID userId);

    Page<FocusSession> findByUserIdOrderByStartedAtDesc(UUID userId, Pageable pageable);

    /** Completed sessions, for the stats aggregate. */
    List<FocusSession> findByUserIdAndCompletedTrue(UUID userId);
}
