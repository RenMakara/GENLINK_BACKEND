package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.Community;
import kh.edu.acleda.campuslink.modules.community.enums.CommunityPrivacy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityRepository extends JpaRepository<Community, UUID> {
    Optional<Community> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /**
     * Biggest public communities the user has not joined, for the home suggestions.
     * excludedIds must be non-empty; callers pass a dummy id when the user has no
     * memberships, because "NOT IN ()" is invalid SQL.
     */
    @Query("SELECT c FROM Community c WHERE c.deletedAt IS NULL AND c.privacy = :privacy "
        + "AND c.id NOT IN :excludedIds ORDER BY c.memberCount DESC")
    List<Community> findSuggestions(CommunityPrivacy privacy, Collection<UUID> excludedIds, Pageable pageable);
}
