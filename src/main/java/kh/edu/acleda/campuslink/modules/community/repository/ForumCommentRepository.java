package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.ForumComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ForumCommentRepository extends JpaRepository<ForumComment, UUID> {

    @Query(value = "SELECT c FROM ForumComment c LEFT JOIN FETCH c.user "
        + "WHERE c.discussion.id = :discussionId AND c.deletedAt IS NULL",
        countQuery = "SELECT COUNT(c) FROM ForumComment c "
            + "WHERE c.discussion.id = :discussionId AND c.deletedAt IS NULL")
    Page<ForumComment> findByDiscussion(@Param("discussionId") UUID discussionId, Pageable pageable);

    Optional<ForumComment> findByIdAndDeletedAtIsNull(UUID id);
}
