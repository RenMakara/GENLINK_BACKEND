package kh.edu.acleda.campuslink.modules.community.service.impl;

import kh.edu.acleda.campuslink.modules.community.dto.request.EventCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.EventResponse;
import kh.edu.acleda.campuslink.modules.community.service.EventService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class EventServiceImpl implements EventService {

    @Override
    public EventResponse create(EventCreateRequest request) {
        // TODO: Implement Event creation using CurrentUserProvider and repository.
        return new EventResponse(null, request.title(), request.description(), request.category(), request.dateTime(), request.locationName(), 0, request.status(), request.language());
    }

    @Override
    public List<EventResponse> getMine() {
        // TODO: Load authenticated user's Event records.
        return List.of();
    }

    @Override
    public EventResponse update(UUID id, EventCreateRequest request) {
        // TODO: Validate ownership, update allowed fields, and persist.
        return new EventResponse(null, request.title(), request.description(), request.category(), request.dateTime(), request.locationName(), 0, request.status(), request.language());
    }

    @Override
    public void delete(UUID id) {
        // TODO: Soft delete the Event record after ownership validation.
    }
}
