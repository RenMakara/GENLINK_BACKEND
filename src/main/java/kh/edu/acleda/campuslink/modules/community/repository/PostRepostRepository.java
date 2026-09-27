package kh.edu.acleda.campuslink.modules.community.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.entity.PostRepost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepostRepository extends JpaRepository<PostRepost, UUID> {

    Optional<PostRepost> findByPostIdAndUserId(UUID postId, UUID userId);

    @Query("SELECT r FROM PostRepost r JOIN FETCH r.user "
        + "WHERE r.post.id IN :postIds AND r.user.id = :userId")
    List<PostRepost> findByPostIdInAndUserId(@Param("postIds") Collection<UUID> postIds,
                                             @Param("userId") UUID userId);

    @Query(value = "SELECT r FROM PostRepost r "
        + "JOIN FETCH r.post p LEFT JOIN FETCH p.user "
        + "WHERE r.user.id = :userId AND p.deletedAt IS NULL "
        + "ORDER BY r.createdAt DESC",
        countQuery = "SELECT COUNT(r) FROM PostRepost r "
            + "WHERE r.user.id = :userId AND r.post.deletedAt IS NULL")
    Page<PostRepost> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = "SELECT r FROM PostRepost r JOIN FETCH r.post p LEFT JOIN FETCH p.user "
        + "WHERE r.user.id = :viewerId AND p.deletedAt IS NULL AND ("
        + "p.visibility = kh.edu.acleda.campuslink.modules.community.enums.Visibility.PUBLIC "
        + "OR p.user.id = :viewerId "
        + "OR (p.visibility = kh.edu.acleda.campuslink.modules.community.enums.Visibility.FRIENDS "
        + "AND p.user.id IN :friendIds) "
        + "OR (p.visibility = kh.edu.acleda.campuslink.modules.community.enums.Visibility.COMMUNITY "
        + "AND p.community.id IN :communityIds)) ORDER BY r.createdAt DESC",
        countQuery = "SELECT COUNT(r) FROM PostRepost r WHERE r.user.id = :viewerId "
            + "AND r.post.deletedAt IS NULL AND ("
            + "r.post.visibility = kh.edu.acleda.campuslink.modules.community.enums.Visibility.PUBLIC "
            + "OR r.post.user.id = :viewerId "
            + "OR (r.post.visibility = kh.edu.acleda.campuslink.modules.community.enums.Visibility.FRIENDS "
            + "AND r.post.user.id IN :friendIds) "
            + "OR (r.post.visibility = kh.edu.acleda.campuslink.modules.community.enums.Visibility.COMMUNITY "
            + "AND r.post.community.id IN :communityIds))")
    Page<PostRepost> findVisibleByUserId(@Param("viewerId") UUID viewerId,
                                         @Param("friendIds") Collection<UUID> friendIds,
                                         @Param("communityIds") Collection<UUID> communityIds,
                                         Pageable pageable);

    long countByPostId(UUID postId);

    @Modifying
    @Query("DELETE FROM PostRepost r WHERE r.post.id = :postId AND r.user.id = :userId")
    int deleteByPostIdAndUserId(@Param("postId") UUID postId, @Param("userId") UUID userId);
}
