package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.Hashtag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HashtagRepository extends JpaRepository<Hashtag, UUID> {
    Optional<Hashtag> findByTag(String tag);

    boolean existsByTag(String tag);
}
