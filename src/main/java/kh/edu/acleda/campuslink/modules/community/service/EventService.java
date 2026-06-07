package kh.edu.acleda.campuslink.modules.community.service;

import kh.edu.acleda.campuslink.modules.community.dto.request.EventCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.EventResponse;

import java.util.List;
import java.util.UUID;

public interface EventService {
    EventResponse create(EventCreateRequest request);
    List<EventResponse> getMine();
    EventResponse update(UUID id, EventCreateRequest request);
    void delete(UUID id);
}
