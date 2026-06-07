package kh.edu.acleda.campuslink.modules.profile.repository;

import kh.edu.acleda.campuslink.modules.profile.entity.SavedItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SavedItemRepository extends JpaRepository<SavedItem, UUID> {}
