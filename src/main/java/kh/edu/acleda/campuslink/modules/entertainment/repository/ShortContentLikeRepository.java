package kh.edu.acleda.campuslink.modules.entertainment.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.ShortContentLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortContentLikeRepository extends JpaRepository<ShortContentLike, UUID> {

    Optional<ShortContentLike> findByUserIdAndShortContentId(UUID userId, UUID shortContentId);

    /** The subset of the given content the user has liked - for the feed's liked flag. */
    List<ShortContentLike> findByUserIdAndShortContentIdIn(UUID userId, Collection<UUID> shortContentIds);
}
