package kh.edu.acleda.campuslink.modules.entertainment.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.ShortContentSave;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortContentSaveRepository extends JpaRepository<ShortContentSave, UUID> {

    Optional<ShortContentSave> findByUserIdAndShortContentId(UUID userId, UUID shortContentId);

    List<ShortContentSave> findByUserIdAndShortContentIdIn(UUID userId, Collection<UUID> shortContentIds);

    /** The user's saved content, newest first. */
    List<ShortContentSave> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
