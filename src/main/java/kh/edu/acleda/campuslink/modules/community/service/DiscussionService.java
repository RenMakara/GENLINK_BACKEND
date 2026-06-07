package kh.edu.acleda.campuslink.modules.community.service;

import kh.edu.acleda.campuslink.modules.community.dto.request.DiscussionCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.DiscussionResponse;

import java.util.List;
import java.util.UUID;

public interface DiscussionService {
    DiscussionResponse create(DiscussionCreateRequest request);
    List<DiscussionResponse> getMine();
    DiscussionResponse update(UUID id, DiscussionCreateRequest request);
    void delete(UUID id);
}
