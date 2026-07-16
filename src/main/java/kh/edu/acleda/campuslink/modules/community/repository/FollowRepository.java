package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.Follow;
import kh.edu.acleda.campuslink.modules.community.enums.FollowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, UUID> {
    Optional<Follow> findByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    boolean existsByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    boolean existsByFollowerIdAndFollowingIdAndStatus(UUID followerId, UUID followingId, FollowStatus status);

    long countByFollowingId(UUID followingId);

    long countByFollowerId(UUID followerId);
}
