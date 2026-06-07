package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.EventRsvp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventRsvpRepository extends JpaRepository<EventRsvp, UUID> {}
