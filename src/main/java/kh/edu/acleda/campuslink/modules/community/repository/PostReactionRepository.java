package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.PostReaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PostReactionRepository extends JpaRepository<PostReaction, UUID> {}
