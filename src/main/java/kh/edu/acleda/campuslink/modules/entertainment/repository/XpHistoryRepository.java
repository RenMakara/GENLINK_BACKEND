package kh.edu.acleda.campuslink.modules.entertainment.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import kh.edu.acleda.campuslink.modules.entertainment.entity.XpHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface XpHistoryRepository extends JpaRepository<XpHistory, UUID> {

    Optional<XpHistory> findByUserIdAndSourceTypeAndSourceId(UUID userId, String sourceType, UUID sourceId);

    @Query("SELECT COALESCE(SUM(x.xpAmount), 0) FROM XpHistory x "
        + "WHERE x.user.id = :userId AND x.createdAt >= :from AND x.createdAt < :to")
    long sumByUserIdBetween(@Param("userId") UUID userId,
                            @Param("from") Instant from,
                            @Param("to") Instant to);

    interface SourceBreakdownProjection {
        String getSourceType();

        Long getXpAmount();

        Long getEntryCount();
    }

    boolean existsByUserIdAndSourceTypeAndSourceId(UUID userId, String sourceType, UUID sourceId);

    /** How many times the user earned XP from a given source - backs badge thresholds. */
    long countByUserIdAndSourceType(UUID userId, String sourceType);

    /** Signed XP totals grouped by source for the admin user detail view. */
    @Query("SELECT x.sourceType AS sourceType, SUM(x.xpAmount) AS xpAmount, COUNT(x) AS entryCount "
        + "FROM XpHistory x WHERE x.user.id = :userId "
        + "GROUP BY x.sourceType ORDER BY SUM(x.xpAmount) DESC, x.sourceType ASC")
    List<SourceBreakdownProjection> summarizeByUserId(@Param("userId") UUID userId);

    /** Newest-first signed XP ledger for the admin user detail view. */
    @Query("SELECT x FROM XpHistory x WHERE x.user.id = :userId "
        + "ORDER BY x.createdAt DESC, x.id DESC")
    Page<XpHistory> findPageByUserId(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Points per user (all sources) since a cutoff, highest first. Pass Instant.EPOCH for
     * all-time - the cutoff is always a comparison, never an IS NULL, so it is portable.
     */
    @Query("SELECT x.user.id, SUM(x.xpAmount) FROM XpHistory x "
        + "WHERE x.createdAt >= :since GROUP BY x.user.id ORDER BY SUM(x.xpAmount) DESC")
    List<Object[]> leaderboardOverall(@Param("since") Instant since);

    /** Points per user restricted to the given sources (a category), highest first. */
    @Query("SELECT x.user.id, SUM(x.xpAmount) FROM XpHistory x "
        + "WHERE x.createdAt >= :since AND x.sourceType IN :sources "
        + "GROUP BY x.user.id ORDER BY SUM(x.xpAmount) DESC")
    List<Object[]> leaderboardByCategory(@Param("since") Instant since,
                                         @Param("sources") Collection<String> sources);
}
