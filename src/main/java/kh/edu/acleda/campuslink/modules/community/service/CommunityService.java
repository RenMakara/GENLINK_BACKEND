package kh.edu.acleda.campuslink.modules.community.service;

import kh.edu.acleda.campuslink.modules.community.dto.request.CommunityCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.CommunityMemberResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.CommunityResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CommunityService {

    /** The creator becomes OWNER and the first member. */
    CommunityResponse create(CommunityCreateRequest request);

    PageResponse<CommunityResponse> discover(String category, Pageable pageable);

    PageResponse<CommunityResponse> getMine(Pageable pageable);

    CommunityResponse getBySlug(String slug);

    CommunityResponse update(UUID communityId, CommunityCreateRequest request);

    void delete(UUID communityId);

    /**
     * Idempotent. A PUBLIC community is joined immediately; a PRIVATE one records a
     * PENDING request that an owner/admin must approve before membership takes effect.
     */
    CommunityResponse join(UUID communityId);

    void leave(UUID communityId);

    PageResponse<CommunityMemberResponse> members(UUID communityId, Pageable pageable);

    /** Pending join requests for a PRIVATE community. Owner/admin only. */
    PageResponse<CommunityMemberResponse> joinRequests(UUID communityId, Pageable pageable);

    /** Approves a pending request, making the user an active member. Owner/admin only. */
    CommunityMemberResponse approveJoinRequest(UUID communityId, UUID userId);

    /** Rejects a pending request. Owner/admin only. */
    void rejectJoinRequest(UUID communityId, UUID userId);
}
