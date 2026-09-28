package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PollVoteRepository extends JpaRepository<PollVote, UUID> {

    Optional<PollVote> findByPollIdAndUserId(UUID pollId, UUID userId);

    boolean existsByPollId(UUID pollId);

    /** The viewer's votes across a page of polls, to resolve myOptionId without an N+1. */
    List<PollVote> findByPollIdInAndUserId(Collection<UUID> pollIds, UUID userId);
}
