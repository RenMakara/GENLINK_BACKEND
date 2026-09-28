package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.ForumDiscussion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;

public interface ForumDiscussionRepository extends JpaRepository<ForumDiscussion, UUID> {

    Optional<ForumDiscussion> findByIdAndDeletedAtIsNull(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM ForumDiscussion d WHERE d.id = :id AND d.deletedAt IS NULL")
    Optional<ForumDiscussion> findByIdAndDeletedAtIsNullForUpdate(@Param("id") UUID id);

    /** Owner-scoped: another user's id reads as not found, never forbidden. */
    Optional<ForumDiscussion> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    /**
     * The forum list. The author is JOIN FETCHed even for anonymous discussions: the
     * row is loaded, then withheld at the DTO boundary, because the service still
     * needs it to answer "is this mine".
     */
    @Query(value = "SELECT d FROM ForumDiscussion d LEFT JOIN FETCH d.user WHERE d.deletedAt IS NULL "
        + "AND (:category IS NULL OR d.category = :category)",
        countQuery = "SELECT COUNT(d) FROM ForumDiscussion d WHERE d.deletedAt IS NULL "
            + "AND (:category IS NULL OR d.category = :category)")
    Page<ForumDiscussion> findFeed(@Param("category") String category, Pageable pageable);

    @Query(value = "SELECT d FROM ForumDiscussion d LEFT JOIN FETCH d.user "
        + "WHERE d.deletedAt IS NULL AND d.user.id = :userId",
        countQuery = "SELECT COUNT(d) FROM ForumDiscussion d WHERE d.deletedAt IS NULL AND d.user.id = :userId")
    Page<ForumDiscussion> findByAuthor(@Param("userId") UUID userId, Pageable pageable);

    /**
     * The admin moderation queue: live discussions that carry reports, most-reported first.
     * Author JOIN FETCHed - the queue reveals it for accountability, so it is loaded, not withheld.
     */
    @Query(value = "SELECT d FROM ForumDiscussion d LEFT JOIN FETCH d.user "
        + "WHERE d.deletedAt IS NULL AND d.reportCount > 0 ORDER BY d.reportCount DESC",
        countQuery = "SELECT COUNT(d) FROM ForumDiscussion d WHERE d.deletedAt IS NULL AND d.reportCount > 0")
    Page<ForumDiscussion> findReportedForModeration(Pageable pageable);

    /** Atomic, like every other counter here: concurrent reactions must not collide. */
    @Modifying
    @Query("UPDATE ForumDiscussion d SET d.reactionCount = d.reactionCount + :delta WHERE d.id = :discussionId")
    void adjustReactionCount(@Param("discussionId") UUID discussionId, @Param("delta") int delta);

    @Modifying
    @Query("UPDATE ForumDiscussion d SET d.commentCount = d.commentCount + :delta WHERE d.id = :discussionId")
    void adjustCommentCount(@Param("discussionId") UUID discussionId, @Param("delta") int delta);

    @Modifying
    @Query("UPDATE ForumDiscussion d SET d.reportCount = d.reportCount + 1 WHERE d.id = :discussionId")
    void incrementReportCount(@Param("discussionId") UUID discussionId);

    /** Used when an admin dismisses a discussion's reports. */
    @Modifying
    @Query("UPDATE ForumDiscussion d SET d.reportCount = 0 WHERE d.id = :discussionId")
    void clearReportCount(@Param("discussionId") UUID discussionId);

    /**
     * Search non-deleted forum discussions by title, content, or category.
     */
    @Query(value = """
        SELECT d FROM ForumDiscussion d LEFT JOIN FETCH d.user
        WHERE d.deletedAt IS NULL
          AND (
               LOWER(d.title) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(d.content) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(d.category) LIKE LOWER(CONCAT('%', :query, '%'))
          )
        ORDER BY d.createdAt DESC
        """,
        countQuery = """
        SELECT COUNT(d) FROM ForumDiscussion d
        WHERE d.deletedAt IS NULL
          AND (
               LOWER(d.title) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(d.content) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(d.category) LIKE LOWER(CONCAT('%', :query, '%'))
          )
        """)
    Page<ForumDiscussion> searchDiscussions(@Param("query") String query, Pageable pageable);
}
