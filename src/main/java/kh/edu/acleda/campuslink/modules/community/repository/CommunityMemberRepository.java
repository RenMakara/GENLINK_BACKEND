package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.CommunityMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CommunityMemberRepository extends JpaRepository<CommunityMember, UUID> {
    Optional<CommunityMember> findByCommunityIdAndUserId(UUID communityId, UUID userId);

    boolean existsByCommunityIdAndUserId(UUID communityId, UUID userId);

    long countByCommunityId(UUID communityId);
}
