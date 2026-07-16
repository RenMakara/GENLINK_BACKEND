package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.PostHashtag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PostHashtagRepository extends JpaRepository<PostHashtag, UUID> {
    List<PostHashtag> findByPostId(UUID postId);

    List<PostHashtag> findByHashtagId(UUID hashtagId);

    boolean existsByPostIdAndHashtagId(UUID postId, UUID hashtagId);

    void deleteByPostId(UUID postId);

    /** Batched for the feed: tags for a whole page in one query. */
    @Query("SELECT ph FROM PostHashtag ph JOIN FETCH ph.hashtag WHERE ph.post.id IN :postIds")
    List<PostHashtag> findByPostIdIn(@Param("postIds") Collection<UUID> postIds);
}
