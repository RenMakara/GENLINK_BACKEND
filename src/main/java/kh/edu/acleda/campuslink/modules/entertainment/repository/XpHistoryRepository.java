package kh.edu.acleda.campuslink.modules.entertainment.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.XpHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface XpHistoryRepository extends JpaRepository<XpHistory, UUID> {

    /** How many times the user earned XP from a given source - backs badge thresholds. */
    long countByUserIdAndSourceType(UUID userId, String sourceType);

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
