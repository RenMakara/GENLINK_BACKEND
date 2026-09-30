package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.Follow;
import kh.edu.acleda.campuslink.modules.community.enums.FollowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, UUID> {
    Optional<Follow> findByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    boolean existsByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    boolean existsByFollowerIdAndFollowingIdAndStatus(UUID followerId, UUID followingId, FollowStatus status);

    /** Counts only settled follows: a pending request is not a follower yet. */
    long countByFollowingIdAndStatus(UUID followingId, FollowStatus status);

    long countByFollowerIdAndStatus(UUID followerId, FollowStatus status);

    @Query(value = "SELECT f FROM Follow f JOIN FETCH f.follower "
        + "WHERE f.following.id = :userId AND f.status = :status",
        countQuery = "SELECT COUNT(f) FROM Follow f WHERE f.following.id = :userId AND f.status = :status")
    Page<Follow> findFollowers(@Param("userId") UUID userId, @Param("status") FollowStatus status, Pageable pageable);

    @Query(value = "SELECT f FROM Follow f JOIN FETCH f.following "
        + "WHERE f.follower.id = :userId AND f.status = :status",
        countQuery = "SELECT COUNT(f) FROM Follow f WHERE f.follower.id = :userId AND f.status = :status")
    Page<Follow> findFollowing(@Param("userId") UUID userId, @Param("status") FollowStatus status, Pageable pageable);

    /** Incoming requests awaiting the user's approval. */
    @Query(value = "SELECT f FROM Follow f JOIN FETCH f.follower "
        + "WHERE f.following.id = :userId AND f.status = kh.edu.acleda.campuslink.modules.community.enums.FollowStatus.PENDING",
        countQuery = "SELECT COUNT(f) FROM Follow f WHERE f.following.id = :userId "
            + "AND f.status = kh.edu.acleda.campuslink.modules.community.enums.FollowStatus.PENDING")
    Page<Follow> findPendingRequests(@Param("userId") UUID userId, Pageable pageable);

    /** Mutual accepted follows, i.e. friends. */
    @Query("SELECT f.following.id FROM Follow f "
        + "WHERE f.follower.id = :userId AND f.status = kh.edu.acleda.campuslink.modules.community.enums.FollowStatus.ACCEPTED "
        + "AND f.following.id IN (SELECT g.follower.id FROM Follow g WHERE g.following.id = :userId "
        + "AND g.status = kh.edu.acleda.campuslink.modules.community.enums.FollowStatus.ACCEPTED)")
    List<UUID> findFriendIds(@Param("userId") UUID userId);

    /** Number of accepted directions in the mutual friendship pair (2 means friends). */
    @Query("SELECT COUNT(f) FROM Follow f WHERE f.status = kh.edu.acleda.campuslink.modules.community.enums.FollowStatus.ACCEPTED "
        + "AND ((f.follower.id = :firstUserId AND f.following.id = :secondUserId) "
        + "OR (f.follower.id = :secondUserId AND f.following.id = :firstUserId))")
    long countMutualAcceptedFollows(@Param("firstUserId") UUID firstUserId,
                                    @Param("secondUserId") UUID secondUserId);

    /** Ids of everyone the user follows (accepted only). */
    @Query("SELECT f.following.id FROM Follow f "
        + "WHERE f.follower.id = :userId "
        + "AND f.status = kh.edu.acleda.campuslink.modules.community.enums.FollowStatus.ACCEPTED")
    List<UUID> findFollowingIds(@Param("userId") UUID userId);
}
