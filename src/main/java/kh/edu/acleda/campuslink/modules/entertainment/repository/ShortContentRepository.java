package kh.edu.acleda.campuslink.modules.entertainment.repository;

import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.ShortContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShortContentRepository extends JpaRepository<ShortContent, UUID> {

    Optional<ShortContent> findByIdAndDeletedAtIsNull(UUID id);

    Page<ShortContent> findByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    Page<ShortContent> findByCategoryAndDeletedAtIsNullOrderByCreatedAtDesc(String category, Pageable pageable);

    // Atomic counter bumps; clear/flush so a re-read in the same tx sees the new value.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ShortContent s SET s.likeCount = s.likeCount + 1 WHERE s.id = :id")
    void incrementLike(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ShortContent s SET s.likeCount = s.likeCount - 1 WHERE s.id = :id AND s.likeCount > 0")
    void decrementLike(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ShortContent s SET s.saveCount = s.saveCount + 1 WHERE s.id = :id")
    void incrementSave(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ShortContent s SET s.saveCount = s.saveCount - 1 WHERE s.id = :id AND s.saveCount > 0")
    void decrementSave(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ShortContent s SET s.shareCount = s.shareCount + 1 WHERE s.id = :id")
    void incrementShare(@Param("id") UUID id);
}
