package kh.edu.acleda.campuslink.modules.profile.repository;

import kh.edu.acleda.campuslink.modules.profile.entity.UserInterest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserInterestRepository extends JpaRepository<UserInterest, UUID> {

    List<UserInterest> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
