package kh.edu.acleda.campuslink.modules.productivity.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.entity.UserSavedMotivation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSavedMotivationRepository extends JpaRepository<UserSavedMotivation, UUID> {

    Optional<UserSavedMotivation> findByUserIdAndMotivationId(UUID userId, UUID motivationId);

    /** The user's saves, newest first - backs both the "saved" flag and the saved list. */
    List<UserSavedMotivation> findByUserIdOrderBySavedAtDesc(UUID userId);
}
