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

    /** Idempotent: joining twice returns the current state. */
    CommunityResponse join(UUID communityId);

    void leave(UUID communityId);

    PageResponse<CommunityMemberResponse> members(UUID communityId, Pageable pageable);
}
