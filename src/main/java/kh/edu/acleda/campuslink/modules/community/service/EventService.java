package kh.edu.acleda.campuslink.modules.community.service;

import kh.edu.acleda.campuslink.modules.community.dto.request.EventCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.EventRegistrationRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.CommunityMemberResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.EventResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.RsvpResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface EventService {

    EventResponse create(EventCreateRequest request);

    /** Published, upcoming events. Optionally filtered by category. */
    PageResponse<EventResponse> browse(String category, Pageable pageable);

    /** Events the caller organises, including drafts. */
    PageResponse<EventResponse> getMine(Pageable pageable);

    EventResponse getById(UUID eventId);

    EventResponse update(UUID eventId, EventCreateRequest request);

    void delete(UUID eventId);

    /**
     * Join. Idempotent. Capacity is checked before any payment, and a paid event is
     * settled against the demo wallet - simulation only.
     */
    /** Legacy bodyless RSVP kept for existing service callers; new clients use the form overload. */
    RsvpResponse rsvp(UUID eventId);

    /** Join and persist the attendee's full registration form. */
    RsvpResponse rsvp(UUID eventId, EventRegistrationRequest request);

    /** Cancelling does not refund: the demo wallet has no refund flow. */
    void cancelRsvp(UUID eventId);

    PageResponse<CommunityMemberResponse> attendees(UUID eventId, Pageable pageable);
}
