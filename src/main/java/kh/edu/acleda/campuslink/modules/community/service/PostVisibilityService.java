package kh.edu.acleda.campuslink.modules.community.service;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.entity.Post;
import kh.edu.acleda.campuslink.modules.community.enums.Visibility;
import kh.edu.acleda.campuslink.modules.community.repository.CommunityMemberRepository;
import kh.edu.acleda.campuslink.modules.community.repository.FollowRepository;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import org.springframework.stereotype.Service;

/** Central post audience policy for resource reads and mutations. */
@Service
public class PostVisibilityService {

    private final FollowRepository followRepository;
    private final CommunityMemberRepository communityMemberRepository;

    public PostVisibilityService(FollowRepository followRepository,
                                 CommunityMemberRepository communityMemberRepository) {
        this.followRepository = followRepository;
        this.communityMemberRepository = communityMemberRepository;
    }

    public boolean canView(Post post, UUID viewerId) {
        if (post == null || viewerId == null) {
            return false;
        }
        UUID authorId = post.getUser() == null ? null : post.getUser().getId();
        if (viewerId.equals(authorId)) {
            return true;
        }
        Visibility visibility = post.getVisibility();
        if (visibility == null) {
            return false;
        }
        return switch (visibility) {
            case PUBLIC -> true;
            case FRIENDS -> authorId != null
                && followRepository.countMutualAcceptedFollows(viewerId, authorId) == 2;
            case COMMUNITY -> post.getCommunity() != null
                && communityMemberRepository.existsByCommunityIdAndUserIdAndStatus(
                    post.getCommunity().getId(), viewerId,
                    kh.edu.acleda.campuslink.modules.community.enums.MemberStatus.ACTIVE);
            case ONLY_ME -> false;
        };
    }

    /** Hide both absent and unauthorized posts with the same not-found response. */
    public void requireCanView(Post post, UUID viewerId) {
        if (!canView(post, viewerId)) {
            throw new NotFoundException("Post not found");
        }
    }
}
