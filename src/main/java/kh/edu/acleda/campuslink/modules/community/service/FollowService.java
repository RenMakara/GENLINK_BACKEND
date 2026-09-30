package kh.edu.acleda.campuslink.modules.community.service;

import kh.edu.acleda.campuslink.modules.community.dto.response.FollowResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.FollowStatsResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface FollowService {

    /** Idempotent. */
    FollowResponse follow(UUID targetUserId);

    void unfollow(UUID targetUserId);

    PageResponse<FollowResponse> followers(UUID userId, Pageable pageable);

    PageResponse<FollowResponse> following(UUID userId, Pageable pageable);

    /** Requests awaiting the caller's approval. */
    PageResponse<FollowResponse> pendingRequests(Pageable pageable);

    FollowResponse acceptRequest(UUID followerUserId);

    void rejectRequest(UUID followerUserId);

    FollowStatsResponse stats(UUID userId);
}
