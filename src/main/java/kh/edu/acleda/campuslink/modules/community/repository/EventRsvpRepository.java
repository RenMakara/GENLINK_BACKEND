package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.EventRsvp;
import kh.edu.acleda.campuslink.modules.community.enums.RsvpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRsvpRepository extends JpaRepository<EventRsvp, UUID> {

    /** Next events the user is attending. Pass a limit-1 Pageable for the home card. */
    List<EventRsvp> findByUserIdAndStatusInAndEvent_DeletedAtIsNullAndEvent_DateTimeAfterOrderByEvent_DateTimeAsc(
        UUID userId, Collection<RsvpStatus> statuses, LocalDateTime after, Pageable pageable);

    Optional<EventRsvp> findByEventIdAndUserId(UUID eventId, UUID userId);

    /** Batched for a list page: which of these events the viewer has responded to. */
    @Query("SELECT r FROM EventRsvp r WHERE r.user.id = :userId AND r.event.id IN :eventIds")
    List<EventRsvp> findByUserIdAndEventIdIn(@Param("userId") UUID userId,
                                             @Param("eventIds") Collection<UUID> eventIds);

    @Query(value = "SELECT r FROM EventRsvp r JOIN FETCH r.user "
        + "WHERE r.event.id = :eventId AND r.status = :status",
        countQuery = "SELECT COUNT(r) FROM EventRsvp r WHERE r.event.id = :eventId AND r.status = :status")
    Page<EventRsvp> findAttendees(@Param("eventId") UUID eventId,
                                  @Param("status") RsvpStatus status,
                                  Pageable pageable);
}
