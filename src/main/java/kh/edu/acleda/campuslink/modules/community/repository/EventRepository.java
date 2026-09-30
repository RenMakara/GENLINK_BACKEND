package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.Event;
import kh.edu.acleda.campuslink.modules.community.enums.EventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    Optional<Event> findByIdAndDeletedAtIsNull(UUID id);

    /** Locks the event row while RSVP state and the attendee counter are changed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id AND e.deletedAt IS NULL")
    Optional<Event> findByIdAndDeletedAtIsNullForUpdate(@Param("id") UUID id);

    /** Locks an existing event row for cancellation, including a soft-deleted row. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") UUID id);

    /** Scoped by organizer, so another user's id reads as not found. */
    Optional<Event> findByIdAndOrganizerIdAndDeletedAtIsNull(UUID id, UUID organizerId);

    /** Browse list. */
    @Query(value = "SELECT e FROM Event e LEFT JOIN FETCH e.organizer WHERE e.deletedAt IS NULL "
        + "AND e.status = :status AND e.dateTime >= :from "
        + "AND (:category IS NULL OR e.category = :category)",
        countQuery = "SELECT COUNT(e) FROM Event e WHERE e.deletedAt IS NULL "
            + "AND e.status = :status AND e.dateTime >= :from "
            + "AND (:category IS NULL OR e.category = :category)")
    Page<Event> findUpcoming(@Param("status") EventStatus status,
                             @Param("from") LocalDateTime from,
                             @Param("category") String category,
                             Pageable pageable);

    @Query(value = "SELECT e FROM Event e LEFT JOIN FETCH e.organizer "
        + "WHERE e.deletedAt IS NULL AND e.organizer.id = :organizerId",
        countQuery = "SELECT COUNT(e) FROM Event e WHERE e.deletedAt IS NULL AND e.organizer.id = :organizerId")
    Page<Event> findByOrganizer(@Param("organizerId") UUID organizerId, Pageable pageable);

    /** Releasing a seat on cancel, without allowing a corrupted negative count. */
    @Modifying
    @Query("UPDATE Event e SET e.attendeeCount = e.attendeeCount - 1 "
        + "WHERE e.id = :eventId AND e.attendeeCount > 0")
    int releaseSeat(@Param("eventId") UUID eventId);

    /** Reserves one seat atomically. */
    @Modifying
    @Query("UPDATE Event e SET e.attendeeCount = e.attendeeCount + 1 "
        + "WHERE e.id = :eventId AND (e.attendeeLimit IS NULL OR e.attendeeCount < e.attendeeLimit)")
    int reserveSeat(@Param("eventId") UUID eventId);

    /**
     * Search published, non-deleted events by title, description, location, or category.
     */
    @Query(value = """
        SELECT e FROM Event e LEFT JOIN FETCH e.organizer
        WHERE e.deletedAt IS NULL
          AND e.status = kh.edu.acleda.campuslink.modules.community.enums.EventStatus.PUBLISHED
          AND (
               LOWER(e.title) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(e.description) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(e.locationName) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(e.category) LIKE LOWER(CONCAT('%', :query, '%'))
          )
        ORDER BY e.dateTime ASC
        """,
        countQuery = """
        SELECT COUNT(e) FROM Event e
        WHERE e.deletedAt IS NULL
          AND e.status = kh.edu.acleda.campuslink.modules.community.enums.EventStatus.PUBLISHED
          AND (
               LOWER(e.title) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(e.description) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(e.locationName) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(e.category) LIKE LOWER(CONCAT('%', :query, '%'))
          )
        """)
    Page<Event> searchEvents(@Param("query") String query, Pageable pageable);
}
