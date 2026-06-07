package kh.edu.acleda.campuslink.modules.community.service;

import kh.edu.acleda.campuslink.modules.community.dto.request.PostCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse;

import java.util.List;
import java.util.UUID;

public interface PostService {
    PostResponse create(PostCreateRequest request);
    List<PostResponse> getMine();
    PostResponse update(UUID id, PostCreateRequest request);
    void delete(UUID id);
}
