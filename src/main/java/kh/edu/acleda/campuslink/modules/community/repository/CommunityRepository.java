package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.Community;
import kh.edu.acleda.campuslink.modules.community.enums.CommunityPrivacy;
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

public interface CommunityRepository extends JpaRepository<Community, UUID> {
    Optional<Community> findBySlug(String slug);

    Optional<Community> findBySlugAndDeletedAtIsNull(String slug);

    Optional<Community> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsBySlug(String slug);

    /** Biggest public communities the user has not joined, for the home suggestions. */
    @Query("SELECT c FROM Community c WHERE c.deletedAt IS NULL AND c.privacy = :privacy "
        + "AND c.id NOT IN :excludedIds ORDER BY c.memberCount DESC")
    List<Community> findSuggestions(CommunityPrivacy privacy, Collection<UUID> excludedIds, Pageable pageable);

    /** Discovery list. Private communities are not browsable. */
    @Query(value = "SELECT c FROM Community c WHERE c.deletedAt IS NULL AND c.privacy = :privacy "
        + "AND (:category IS NULL OR c.category = :category)",
        countQuery = "SELECT COUNT(c) FROM Community c WHERE c.deletedAt IS NULL AND c.privacy = :privacy "
            + "AND (:category IS NULL OR c.category = :category)")
    Page<Community> findDiscoverable(@Param("privacy") CommunityPrivacy privacy,
                                     @Param("category") String category,
                                     Pageable pageable);

    /** Straight to the database, so concurrent joins cannot both read 9 and write 10. */
    @Modifying
    @Query("UPDATE Community c SET c.memberCount = c.memberCount + :delta WHERE c.id = :communityId")
    void adjustMemberCount(@Param("communityId") UUID communityId, @Param("delta") int delta);

    @Modifying
    @Query("UPDATE Community c SET c.postCount = c.postCount + :delta WHERE c.id = :communityId")
    void adjustPostCount(@Param("communityId") UUID communityId, @Param("delta") int delta);
}
