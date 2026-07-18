package kh.edu.acleda.campuslink.modules.community.service.impl;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.response.FollowResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.FollowStatsResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse;
import kh.edu.acleda.campuslink.modules.community.entity.Follow;
import kh.edu.acleda.campuslink.modules.community.enums.FollowStatus;
import kh.edu.acleda.campuslink.modules.community.repository.FollowRepository;
import kh.edu.acleda.campuslink.modules.community.service.FollowService;
import kh.edu.acleda.campuslink.modules.profile.entity.UserSetting;
import kh.edu.acleda.campuslink.modules.profile.repository.UserSettingRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FollowServiceImpl implements FollowService {

    private static final String PRIVATE = "PRIVATE";

    private final FollowRepository followRepository;
    private final UserSettingRepository userSettingRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public FollowServiceImpl(FollowRepository followRepository,
                             UserSettingRepository userSettingRepository,
                             UserRepository userRepository,
                             CurrentUserProvider currentUserProvider) {
        this.followRepository = followRepository;
        this.userSettingRepository = userSettingRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public FollowResponse follow(UUID targetUserId) {
        UUID followerId = currentUserProvider.getCurrentUserId();
        // Also guarded by chk_follows_no_self_follow, but a clear error beats a
        // constraint violation surfacing as a 500.
        if (followerId.equals(targetUserId)) {
            throw new ApiException("You cannot follow yourself", "FOLLOW_SELF_NOT_ALLOWED");
        }

        User target = loadVisibleUser(targetUserId);
        FollowStatus status = requiresApproval(targetUserId) ? FollowStatus.PENDING : FollowStatus.ACCEPTED;

        // Idempotent: Follow backs a toggle, and (follower_id, following_id) is unique.
        return followRepository.findByFollowerIdAndFollowingId(followerId, targetUserId)
            .map(existing -> new FollowResponse(summary(target), existing.getStatus()))
            .orElseGet(() -> {
                Follow follow = followRepository.save(Follow.builder()
                    .follower(loadCurrentUser())
                    .following(target)
                    .status(status)
                    .build());
                return new FollowResponse(summary(target), follow.getStatus());
            });
    }

    @Override
    @Transactional
    public void unfollow(UUID targetUserId) {
        UUID followerId = currentUserProvider.getCurrentUserId();
        followRepository.findByFollowerIdAndFollowingId(followerId, targetUserId)
            .ifPresent(followRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FollowResponse> followers(UUID userId, Pageable pageable) {
        return PageResponse.from(followRepository.findFollowers(userId, FollowStatus.ACCEPTED, pageable),
            follow -> new FollowResponse(summary(follow.getFollower()), follow.getStatus()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FollowResponse> following(UUID userId, Pageable pageable) {
        return PageResponse.from(followRepository.findFollowing(userId, FollowStatus.ACCEPTED, pageable),
            follow -> new FollowResponse(summary(follow.getFollowing()), follow.getStatus()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FollowResponse> pendingRequests(Pageable pageable) {
        return PageResponse.from(
            followRepository.findPendingRequests(currentUserProvider.getCurrentUserId(), pageable),
            follow -> new FollowResponse(summary(follow.getFollower()), follow.getStatus()));
    }

    @Override
    @Transactional
    public FollowResponse acceptRequest(UUID followerUserId) {
        Follow follow = pendingRequest(followerUserId);
        follow.setStatus(FollowStatus.ACCEPTED);
        followRepository.save(follow);
        return new FollowResponse(summary(follow.getFollower()), FollowStatus.ACCEPTED);
    }

    @Override
    @Transactional
    public void rejectRequest(UUID followerUserId) {
        followRepository.delete(pendingRequest(followerUserId));
    }

    @Override
    @Transactional(readOnly = true)
    public FollowStatsResponse stats(UUID userId) {
        UUID viewerId = currentUserProvider.getCurrentUserId();
        FollowStatus myStatus = followRepository.findByFollowerIdAndFollowingId(viewerId, userId)
            .map(Follow::getStatus)
            .orElse(null);
        boolean friend = FollowStatus.ACCEPTED.equals(myStatus)
            && followRepository.existsByFollowerIdAndFollowingIdAndStatus(userId, viewerId, FollowStatus.ACCEPTED);

        return new FollowStatsResponse(
            followRepository.countByFollowingIdAndStatus(userId, FollowStatus.ACCEPTED),
            followRepository.countByFollowerIdAndStatus(userId, FollowStatus.ACCEPTED),
            myStatus,
            friend
        );
    }

    private Follow pendingRequest(UUID followerUserId) {
        Follow follow = followRepository
            .findByFollowerIdAndFollowingId(followerUserId, currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("Follow request not found"));
        if (follow.getStatus() != FollowStatus.PENDING) {
            throw new NotFoundException("Follow request not found");
        }
        return follow;
    }

    /** A PRIVATE profile gates its follower list, mirroring getPublicProfile. */
    private boolean requiresApproval(UUID targetUserId) {
        return userSettingRepository.findByUserId(targetUserId)
            .map(UserSetting::getPrivacyProfile)
            .filter(PRIVATE::equalsIgnoreCase)
            .isPresent();
    }

    private User loadVisibleUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));
        // Banned and deleted accounts read as not found, same as the public profile.
        if (user.getAccountStatus() == AccountStatus.BANNED
            || user.getAccountStatus() == AccountStatus.DELETED
            || user.getDeletedAt() != null) {
            throw new NotFoundException("User not found");
        }
        return user;
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private PostResponse.AuthorSummary summary(User user) {
        return new PostResponse.AuthorSummary(
            user.getId(), user.getFullName(), user.getUsername(), user.getAvatarUrl());
    }
}
