package kh.edu.acleda.campuslink.modules.community.service;

import kh.edu.acleda.campuslink.modules.community.dto.request.PostCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PostService {

    PostResponse create(PostCreateRequest request);

    /** The public feed, newest first. */
    PageResponse<PostResponse> getFeed(Pageable pageable);

    PageResponse<PostResponse> getMine(Pageable pageable);

    PostResponse getById(UUID id);

    PostResponse update(UUID id, PostCreateRequest request);

    void delete(UUID id);

    /** Idempotent: backs a UI toggle. @return the post's like count afterwards. */
    int like(UUID postId);

    int unlike(UUID postId);
}
