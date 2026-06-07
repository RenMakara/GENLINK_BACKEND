package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.ForumComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ForumCommentRepository extends JpaRepository<ForumComment, UUID> {}
