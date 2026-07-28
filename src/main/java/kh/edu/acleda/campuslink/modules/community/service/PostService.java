package kh.edu.acleda.campuslink.modules.community.service;

import kh.edu.acleda.campuslink.modules.community.dto.request.PostCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PostService {

    PostResponse create(PostCreateRequest request);

    /**
     * The personalised feed, newest first: public posts, the viewer's own, friends-only
     * posts from mutual follows, and community posts from communities they are in.
     */
    PageResponse<PostResponse> getFeed(Pageable pageable);

    PageResponse<PostResponse> getCommunityFeed(UUID communityId, Pageable pageable);

    PageResponse<PostResponse> getMine(Pageable pageable);

    PostResponse getById(UUID id);

    PostResponse update(UUID id, PostCreateRequest request);

    void delete(UUID id);

    /**
     * Removes a post as a community moderator. Allowed to OWNER / ADMIN / MODERATOR of the
     * post's community; distinct from {@link #delete} which is author-only. Soft delete.
     */
    void removeAsModerator(UUID postId);

    /** Idempotent: backs a UI toggle. @return the post's like count afterwards. */
    int like(UUID postId);

    int unlike(UUID postId);
}
