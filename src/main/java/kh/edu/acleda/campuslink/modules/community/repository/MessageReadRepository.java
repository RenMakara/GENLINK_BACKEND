package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.MessageRead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MessageReadRepository extends JpaRepository<MessageRead, UUID> {}
