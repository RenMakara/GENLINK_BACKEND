package kh.edu.acleda.campuslink.modules.profile.repository;

import kh.edu.acleda.campuslink.modules.profile.entity.SavedItem;
import kh.edu.acleda.campuslink.modules.profile.enums.SavedItemType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedItemRepository extends JpaRepository<SavedItem, UUID> {

    List<SavedItem> findByUserIdOrderBySavedAtDesc(UUID userId);

    List<SavedItem> findByUserIdAndItemTypeOrderBySavedAtDesc(UUID userId, SavedItemType itemType);

    Optional<SavedItem> findByUserIdAndItemTypeAndItemId(UUID userId, SavedItemType itemType, UUID itemId);

    boolean existsByUserIdAndItemTypeAndItemId(UUID userId, SavedItemType itemType, UUID itemId);
}
