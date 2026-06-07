package kh.edu.acleda.campuslink.modules.community.service.impl;

import kh.edu.acleda.campuslink.modules.community.dto.request.DiscussionCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.DiscussionResponse;
import kh.edu.acleda.campuslink.modules.community.service.DiscussionService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class DiscussionServiceImpl implements DiscussionService {

    @Override
    public DiscussionResponse create(DiscussionCreateRequest request) {
        // TODO: Implement Discussion creation using CurrentUserProvider and repository.
        return new DiscussionResponse(null, request.title(), request.content(), request.category(), request.anonymous(), request.language(), 0, 0);
    }

    @Override
    public List<DiscussionResponse> getMine() {
        // TODO: Load authenticated user's Discussion records.
        return List.of();
    }

    @Override
    public DiscussionResponse update(UUID id, DiscussionCreateRequest request) {
        // TODO: Validate ownership, update allowed fields, and persist.
        return new DiscussionResponse(null, request.title(), request.content(), request.category(), request.anonymous(), request.language(), 0, 0);
    }

    @Override
    public void delete(UUID id) {
        // TODO: Soft delete the Discussion record after ownership validation.
    }
}
