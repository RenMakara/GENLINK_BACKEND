package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.Event;
import kh.edu.acleda.campuslink.modules.community.enums.EventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    Optional<Event> findByIdAndDeletedAtIsNull(UUID id);

    /** Scoped by organizer, so another user's id reads as not found. */
    Optional<Event> findByIdAndOrganizerIdAndDeletedAtIsNull(UUID id, UUID organizerId);

    /**
     * Browse list. Only PUBLISHED events are discoverable - a DRAFT belongs to its
     * organizer alone - and past events are filtered out.
     */
    @Query(value = "SELECT e FROM Event e JOIN FETCH e.organizer WHERE e.deletedAt IS NULL "
        + "AND e.status = :status AND e.dateTime >= :from "
        + "AND (:category IS NULL OR e.category = :category)",
        countQuery = "SELECT COUNT(e) FROM Event e WHERE e.deletedAt IS NULL "
            + "AND e.status = :status AND e.dateTime >= :from "
            + "AND (:category IS NULL OR e.category = :category)")
    Page<Event> findUpcoming(@Param("status") EventStatus status,
                             @Param("from") LocalDateTime from,
                             @Param("category") String category,
                             Pageable pageable);

    @Query(value = "SELECT e FROM Event e JOIN FETCH e.organizer "
        + "WHERE e.deletedAt IS NULL AND e.organizer.id = :organizerId",
        countQuery = "SELECT COUNT(e) FROM Event e WHERE e.deletedAt IS NULL AND e.organizer.id = :organizerId")
    Page<Event> findByOrganizer(@Param("organizerId") UUID organizerId, Pageable pageable);

    /**
     * Straight to the database rather than read-modify-write: two people joining at
     * once must not both read 9 and both write 10, which would push an event past its
     * capacity.
     */
    @Modifying
    @Query("UPDATE Event e SET e.attendeeCount = e.attendeeCount + :delta WHERE e.id = :eventId")
    void adjustAttendeeCount(@Param("eventId") UUID eventId, @Param("delta") int delta);
}
