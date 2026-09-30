package kh.edu.acleda.campuslink.modules.community.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.EventCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.EventRegistrationRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.CommunityMemberResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.EventResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.RsvpResponse;
import kh.edu.acleda.campuslink.modules.community.service.EventService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/community/events")
@Tag(name = "Community - Events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EventResponse>> create(@Valid @RequestBody EventCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Event created", eventService.create(request)));
    }

    /** Published upcoming events, soonest first. Rows carry organizer and myRsvpStatus. */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<EventResponse>>> browse(
        @RequestParam(required = false) String category,
        @PageableDefault(size = 20, sort = "dateTime", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Events retrieved", eventService.browse(category, pageable)));
    }

    /** Literal path, declared before /{eventId} so it cannot be shadowed. */
    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<PageResponse<EventResponse>>> getMine(
        @PageableDefault(size = 20, sort = "dateTime", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("My events retrieved", eventService.getMine(pageable)));
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<ApiResponse<EventResponse>> getById(@PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success("Event retrieved", eventService.getById(eventId)));
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<ApiResponse<EventResponse>> update(@PathVariable UUID eventId,
                                                             @Valid @RequestBody EventCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Event updated", eventService.update(eventId, request)));
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID eventId) {
        eventService.delete(eventId);
        return ResponseEntity.ok(ApiResponse.success("Event deleted", null));
    }

    /** Join with the full attendee registration form. */
    @PostMapping("/{eventId}/rsvp")
    public ResponseEntity<ApiResponse<RsvpResponse>> rsvp(
        @PathVariable UUID eventId,
        @Valid @RequestBody EventRegistrationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("RSVP confirmed", eventService.rsvp(eventId, request)));
    }

    @DeleteMapping("/{eventId}/rsvp")
    public ResponseEntity<ApiResponse<Void>> cancelRsvp(@PathVariable UUID eventId) {
        eventService.cancelRsvp(eventId);
        return ResponseEntity.ok(ApiResponse.success("RSVP cancelled", null));
    }

    @GetMapping("/{eventId}/attendees")
    public ResponseEntity<ApiResponse<PageResponse<CommunityMemberResponse>>> attendees(
        @PathVariable UUID eventId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Attendees retrieved",
            eventService.attendees(eventId, pageable)));
    }
}
