package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.PostHashtag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostHashtagRepository extends JpaRepository<PostHashtag, UUID> {
    List<PostHashtag> findByPostId(UUID postId);

    List<PostHashtag> findByHashtagId(UUID hashtagId);

    boolean existsByPostIdAndHashtagId(UUID postId, UUID hashtagId);
}
