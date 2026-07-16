package kh.edu.acleda.campuslink.modules.profile.repository;

import kh.edu.acleda.campuslink.modules.profile.entity.Interest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface InterestRepository extends JpaRepository<Interest, UUID> {

    List<Interest> findByIsActiveTrueOrderByNameEnAsc();

    List<Interest> findByIdInAndIsActiveTrue(Collection<UUID> ids);
}
