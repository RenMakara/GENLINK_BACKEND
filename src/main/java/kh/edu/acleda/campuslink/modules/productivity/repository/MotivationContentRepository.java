package kh.edu.acleda.campuslink.modules.productivity.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.entity.MotivationContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MotivationContentRepository extends JpaRepository<MotivationContent, UUID> {

    List<MotivationContent> findByDeletedAtIsNull();

    Optional<MotivationContent> findByIdAndDeletedAtIsNull(UUID id);

    List<MotivationContent> findByDeletedAtIsNullOrderByCreatedAtDesc();

    List<MotivationContent> findByCategoryAndDeletedAtIsNullOrderByCreatedAtDesc(String category);

    /** Atomic counter bumps so concurrent saves cannot lose an increment. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MotivationContent m SET m.savedCount = m.savedCount + 1 WHERE m.id = :id")
    void incrementSavedCount(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MotivationContent m SET m.savedCount = m.savedCount - 1 "
        + "WHERE m.id = :id AND m.savedCount > 0")
    void decrementSavedCount(@Param("id") UUID id);
}
