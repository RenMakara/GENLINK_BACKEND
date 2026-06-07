package kh.edu.acleda.campuslink.modules.productivity.repository;

import kh.edu.acleda.campuslink.modules.productivity.entity.MotivationContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MotivationContentRepository extends JpaRepository<MotivationContent, UUID> {}
