package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.Post;
import kh.edu.acleda.campuslink.modules.community.enums.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    /**
     * Recent public posts for the home feed preview. Joins the author eagerly:
     * the preview reads user.fullName, which would otherwise fire one query per post.
     */
    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.user WHERE p.deletedAt IS NULL AND p.visibility = :visibility ORDER BY p.createdAt DESC")
    List<Post> findFeedPreview(Visibility visibility, Pageable pageable);

    /**
     * The feed. countQuery is given explicitly because JOIN FETCH cannot appear in a
     * count query, and Spring would otherwise fail to derive one.
     */
    @Query(value = "SELECT p FROM Post p LEFT JOIN FETCH p.user "
        + "WHERE p.deletedAt IS NULL AND p.visibility = :visibility",
        countQuery = "SELECT COUNT(p) FROM Post p WHERE p.deletedAt IS NULL AND p.visibility = :visibility")
    Page<Post> findFeed(@Param("visibility") Visibility visibility, Pageable pageable);

    @Query(value = "SELECT p FROM Post p LEFT JOIN FETCH p.user WHERE p.deletedAt IS NULL AND p.user.id = :userId",
        countQuery = "SELECT COUNT(p) FROM Post p WHERE p.deletedAt IS NULL AND p.user.id = :userId")
    Page<Post> findByAuthor(@Param("userId") UUID userId, Pageable pageable);

    /**
     * The personalised feed: everything the viewer is entitled to see.
     *
     * <p>PUBLIC is open, FRIENDS needs a mutual accepted follow, COMMUNITY needs an
     * active membership, and the viewer always sees their own posts regardless of
     * visibility. friendIds and communityIds must be non-empty - "IN ()" is invalid
     * SQL - so callers pass a placeholder id when the viewer has neither.
     */
    @Query(value = "SELECT p FROM Post p LEFT JOIN FETCH p.user WHERE p.deletedAt IS NULL AND ("
        + "p.visibility = kh.edu.acleda.campuslink.modules.community.enums.Visibility.PUBLIC "
        + "OR p.user.id = :viewerId "
        + "OR (p.visibility = kh.edu.acleda.campuslink.modules.community.enums.Visibility.FRIENDS "
        + "AND p.user.id IN :friendIds) "
        + "OR (p.visibility = kh.edu.acleda.campuslink.modules.community.enums.Visibility.COMMUNITY "
        + "AND p.community.id IN :communityIds))",
        countQuery = "SELECT COUNT(p) FROM Post p WHERE p.deletedAt IS NULL AND ("
            + "p.visibility = kh.edu.acleda.campuslink.modules.community.enums.Visibility.PUBLIC "
            + "OR p.user.id = :viewerId "
            + "OR (p.visibility = kh.edu.acleda.campuslink.modules.community.enums.Visibility.FRIENDS "
            + "AND p.user.id IN :friendIds) "
            + "OR (p.visibility = kh.edu.acleda.campuslink.modules.community.enums.Visibility.COMMUNITY "
            + "AND p.community.id IN :communityIds))")
    Page<Post> findVisibleFeed(@Param("viewerId") UUID viewerId,
                               @Param("friendIds") Collection<UUID> friendIds,
                               @Param("communityIds") Collection<UUID> communityIds,
                               Pageable pageable);

    @Query(value = "SELECT p FROM Post p LEFT JOIN FETCH p.user "
        + "WHERE p.deletedAt IS NULL AND p.community.id = :communityId",
        countQuery = "SELECT COUNT(p) FROM Post p WHERE p.deletedAt IS NULL AND p.community.id = :communityId")
    Page<Post> findByCommunity(@Param("communityId") UUID communityId, Pageable pageable);

    @Query(value = "SELECT p FROM Post p LEFT JOIN FETCH p.user "
        + "WHERE p.deletedAt IS NULL AND p.community.id = :communityId AND ("
        + "p.visibility = kh.edu.acleda.campuslink.modules.community.enums.Visibility.PUBLIC "
        + "OR p.user.id = :viewerId "
        + "OR (p.visibility = kh.edu.acleda.campuslink.modules.community.enums.Visibility.FRIENDS "
        + "AND p.user.id IN :friendIds) "
        + "OR (p.visibility = kh.edu.acleda.campuslink.modules.community.enums.Visibility.COMMUNITY "
        + "AND p.community.id IN :communityIds))",
        countQuery = "SELECT COUNT(p) FROM Post p WHERE p.deletedAt IS NULL AND p.community.id = :communityId AND ("
            + "p.visibility = kh.edu.acleda.campuslink.modules.community.enums.Visibility.PUBLIC "
            + "OR p.user.id = :viewerId "
            + "OR (p.visibility = kh.edu.acleda.campuslink.modules.community.enums.Visibility.FRIENDS "
            + "AND p.user.id IN :friendIds) "
            + "OR (p.visibility = kh.edu.acleda.campuslink.modules.community.enums.Visibility.COMMUNITY "
            + "AND p.community.id IN :communityIds))")
    Page<Post> findVisibleByCommunity(@Param("communityId") UUID communityId,
                                     @Param("viewerId") UUID viewerId,
                                     @Param("friendIds") Collection<UUID> friendIds,
                                     @Param("communityIds") Collection<UUID> communityIds,
                                     Pageable pageable);

    /** A community moderator's queue: live posts in the community that carry reports, most-reported first. */
    @Query(value = "SELECT p FROM Post p LEFT JOIN FETCH p.user "
        + "WHERE p.deletedAt IS NULL AND p.community.id = :communityId AND p.reportCount > 0 "
        + "ORDER BY p.reportCount DESC",
        countQuery = "SELECT COUNT(p) FROM Post p "
            + "WHERE p.deletedAt IS NULL AND p.community.id = :communityId AND p.reportCount > 0")
    Page<Post> findReportedByCommunity(@Param("communityId") UUID communityId, Pageable pageable);

    /** Scoped by owner so another user's id reads as not found rather than forbidden. */
    Optional<Post> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    Optional<Post> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT p FROM Post p WHERE p.id = :id AND p.deletedAt IS NULL")
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<Post> findByIdAndDeletedAtIsNullForUpdate(@Param("id") UUID id);

    @Query("SELECT p FROM Post p WHERE p.id = :id AND p.user.id = :userId AND p.deletedAt IS NULL")
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<Post> findByIdAndUserIdAndDeletedAtIsNullForUpdate(@Param("id") UUID id,
                                                                @Param("userId") UUID userId);

    /**
     * Counter updates go straight to the database rather than read-modify-write, so two
     * concurrent likes cannot both read 4 and both write 5.
     */
    @Modifying
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + :delta WHERE p.id = :postId")
    void adjustLikeCount(@Param("postId") UUID postId, @Param("delta") int delta);

    @Modifying
    @Query("UPDATE Post p SET p.commentCount = p.commentCount + :delta WHERE p.id = :postId")
    void adjustCommentCount(@Param("postId") UUID postId, @Param("delta") int delta);

    @Modifying
    @Query("UPDATE Post p SET p.shareCount = p.shareCount + :delta WHERE p.id = :postId")
    void adjustShareCount(@Param("postId") UUID postId, @Param("delta") int delta);

    @Modifying
    @Query("UPDATE Post p SET p.reportCount = p.reportCount + :delta WHERE p.id = :postId")
    void adjustReportCount(@Param("postId") UUID postId, @Param("delta") int delta);

    /** Used when a moderator dismisses a post's reports. */
    @Modifying
    @Query("UPDATE Post p SET p.reportCount = 0 WHERE p.id = :postId")
    void clearReportCount(@Param("postId") UUID postId);

    /**
     * Search public, non-deleted posts by content text. Eagerly fetches author.
     */
    @Query(value = """
        SELECT p FROM Post p LEFT JOIN FETCH p.user
        WHERE p.deletedAt IS NULL
          AND p.visibility = kh.edu.acleda.campuslink.modules.community.enums.Visibility.PUBLIC
          AND LOWER(p.contentText) LIKE LOWER(CONCAT('%', :query, '%'))
        ORDER BY p.createdAt DESC
        """,
        countQuery = """
        SELECT COUNT(p) FROM Post p
        WHERE p.deletedAt IS NULL
          AND p.visibility = kh.edu.acleda.campuslink.modules.community.enums.Visibility.PUBLIC
          AND LOWER(p.contentText) LIKE LOWER(CONCAT('%', :query, '%'))
        """)
    Page<Post> searchPublicPosts(@Param("query") String query, Pageable pageable);
}
