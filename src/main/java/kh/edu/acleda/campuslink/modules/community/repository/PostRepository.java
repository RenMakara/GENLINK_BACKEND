package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.Post;
import kh.edu.acleda.campuslink.modules.community.enums.Visibility;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    /**
     * Recent public posts for the home feed preview. Joins the author eagerly:
     * the preview reads user.fullName, which would otherwise fire one query per post.
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.user WHERE p.deletedAt IS NULL AND p.visibility = :visibility ORDER BY p.createdAt DESC")
    List<Post> findFeedPreview(Visibility visibility, Pageable pageable);
}
