package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.Hashtag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HashtagRepository extends JpaRepository<Hashtag, UUID> {
    Optional<Hashtag> findByTag(String tag);

    boolean existsByTag(String tag);

    List<Hashtag> findByTagIn(Collection<String> tags);

    /** Backs "Trending" in the Social Feed flow. */
    List<Hashtag> findByOrderByUsageCountDesc(Pageable pageable);

    /** Global trending must not reveal tags that appear only on restricted posts. */
    @Query("SELECT h.tag FROM PostHashtag ph JOIN ph.hashtag h JOIN ph.post p "
        + "WHERE p.deletedAt IS NULL "
        + "AND p.visibility = kh.edu.acleda.campuslink.modules.community.enums.Visibility.PUBLIC "
        + "GROUP BY h.tag ORDER BY COUNT(DISTINCT p.id) DESC, h.tag ASC")
    List<String> findTrendingPublicTags(Pageable pageable);
}
