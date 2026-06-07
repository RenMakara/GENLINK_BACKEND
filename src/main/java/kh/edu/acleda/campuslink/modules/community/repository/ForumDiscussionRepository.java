package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.ForumDiscussion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ForumDiscussionRepository extends JpaRepository<ForumDiscussion, UUID> {}
