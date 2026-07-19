package kh.edu.acleda.campuslink.modules.entertainment.repository;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBadgeRepository extends JpaRepository<UserBadge, UUID> {

    List<UserBadge> findByUserIdOrderByUnlockedAtDesc(UUID userId);

    boolean existsByUserIdAndBadgeId(UUID userId, UUID badgeId);
}
