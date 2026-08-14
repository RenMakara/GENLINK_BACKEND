package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.Poll;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PollRepository extends JpaRepository<Poll, UUID> {

    Optional<Poll> findByPostId(UUID postId);

    /** Batch-load the polls for a page of posts, to avoid a query per poll row in the feed. */
    List<Poll> findByPostIdIn(Collection<UUID> postIds);
}
