package kh.edu.acleda.campuslink.modules.profile.repository;

import kh.edu.acleda.campuslink.modules.profile.entity.SavedItem;
import kh.edu.acleda.campuslink.modules.profile.enums.SavedItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedItemRepository extends JpaRepository<SavedItem, UUID> {

    List<SavedItem> findByUserIdOrderBySavedAtDesc(UUID userId);

    List<SavedItem> findByUserIdAndItemTypeOrderBySavedAtDesc(UUID userId, SavedItemType itemType);

    Optional<SavedItem> findByUserIdAndItemTypeAndItemId(UUID userId, SavedItemType itemType, UUID itemId);

    boolean existsByUserIdAndItemTypeAndItemId(UUID userId, SavedItemType itemType, UUID itemId);

    /**
     * Which of these items the user has saved, in one query. Lets a feed page resolve
     * every save toggle at once instead of asking per row.
     */
    @Query("SELECT s.itemId FROM SavedItem s "
        + "WHERE s.user.id = :userId AND s.itemType = :itemType AND s.itemId IN :itemIds")
    List<UUID> findSavedItemIds(@Param("userId") UUID userId,
                                @Param("itemType") SavedItemType itemType,
                                @Param("itemIds") Collection<UUID> itemIds);
}
