package kh.edu.acleda.campuslink.modules.entertainment.repository;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.Badge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BadgeRepository extends JpaRepository<Badge, UUID> {

    List<Badge> findByDeletedAtIsNullOrderByCreatedAtAsc();
}
