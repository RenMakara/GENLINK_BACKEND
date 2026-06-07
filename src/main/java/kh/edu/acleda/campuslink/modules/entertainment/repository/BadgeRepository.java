package kh.edu.acleda.campuslink.modules.entertainment.repository;

import kh.edu.acleda.campuslink.modules.entertainment.entity.Badge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BadgeRepository extends JpaRepository<Badge, UUID> {}
