package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.EventRsvp;
import kh.edu.acleda.campuslink.modules.community.enums.RsvpStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EventRsvpRepository extends JpaRepository<EventRsvp, UUID> {

    /** Next events the user is attending. Pass a limit-1 Pageable for the home card. */
    List<EventRsvp> findByUserIdAndStatusInAndEvent_DeletedAtIsNullAndEvent_DateTimeAfterOrderByEvent_DateTimeAsc(
        UUID userId, Collection<RsvpStatus> statuses, LocalDateTime after, Pageable pageable);
}
