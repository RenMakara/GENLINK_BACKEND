package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.PollOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PollOptionRepository extends JpaRepository<PollOption, UUID> {}
