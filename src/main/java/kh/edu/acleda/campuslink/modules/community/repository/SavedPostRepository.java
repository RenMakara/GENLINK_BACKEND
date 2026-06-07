package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.SavedPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SavedPostRepository extends JpaRepository<SavedPost, UUID> {}
