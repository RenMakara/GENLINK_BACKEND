package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.CommunityMember;
import kh.edu.acleda.campuslink.modules.community.enums.MemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityMemberRepository extends JpaRepository<CommunityMember, UUID> {
    Optional<CommunityMember> findByCommunityIdAndUserId(UUID communityId, UUID userId);

    List<CommunityMember> findByUserId(UUID userId);

    boolean existsByCommunityIdAndUserId(UUID communityId, UUID userId);

    long countByCommunityId(UUID communityId);

    /** Ids of communities the user is an active member of; drives COMMUNITY visibility. */
    @Query("SELECT m.community.id FROM CommunityMember m WHERE m.user.id = :userId "
        + "AND m.status = kh.edu.acleda.campuslink.modules.community.enums.MemberStatus.ACTIVE "
        + "AND m.community.deletedAt IS NULL")
    List<UUID> findActiveCommunityIds(@Param("userId") UUID userId);

    @Query(value = "SELECT m FROM CommunityMember m JOIN FETCH m.user "
        + "WHERE m.community.id = :communityId AND m.status = :status",
        countQuery = "SELECT COUNT(m) FROM CommunityMember m WHERE m.community.id = :communityId AND m.status = :status")
    Page<CommunityMember> findMembers(@Param("communityId") UUID communityId,
                                      @Param("status") MemberStatus status,
                                      Pageable pageable);

    @Query(value = "SELECT m FROM CommunityMember m JOIN FETCH m.community "
        + "WHERE m.user.id = :userId AND m.status = :status AND m.community.deletedAt IS NULL",
        countQuery = "SELECT COUNT(m) FROM CommunityMember m WHERE m.user.id = :userId AND m.status = :status "
            + "AND m.community.deletedAt IS NULL")
    Page<CommunityMember> findMyCommunities(@Param("userId") UUID userId,
                                            @Param("status") MemberStatus status,
                                            Pageable pageable);
}
