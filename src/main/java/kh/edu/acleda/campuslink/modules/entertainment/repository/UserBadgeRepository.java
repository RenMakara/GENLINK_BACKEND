package kh.edu.acleda.campuslink.modules.entertainment.repository;

import kh.edu.acleda.campuslink.modules.entertainment.entity.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserBadgeRepository extends JpaRepository<UserBadge, UUID> {}
