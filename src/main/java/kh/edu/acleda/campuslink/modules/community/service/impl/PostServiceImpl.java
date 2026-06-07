package kh.edu.acleda.campuslink.modules.community.service.impl;

import kh.edu.acleda.campuslink.modules.community.dto.request.PostCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse;
import kh.edu.acleda.campuslink.modules.community.service.PostService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PostServiceImpl implements PostService {

    @Override
    public PostResponse create(PostCreateRequest request) {
        // TODO: Implement Post creation using CurrentUserProvider and repository.
        return new PostResponse(null, request.contentText(), request.imageUrl(), request.postType(), request.visibility(), request.language(), 0, 0);
    }

    @Override
    public List<PostResponse> getMine() {
        // TODO: Load authenticated user's Post records.
        return List.of();
    }

    @Override
    public PostResponse update(UUID id, PostCreateRequest request) {
        // TODO: Validate ownership, update allowed fields, and persist.
        return new PostResponse(null, request.contentText(), request.imageUrl(), request.postType(), request.visibility(), request.language(), 0, 0);
    }

    @Override
    public void delete(UUID id) {
        // TODO: Soft delete the Post record after ownership validation.
    }
}
