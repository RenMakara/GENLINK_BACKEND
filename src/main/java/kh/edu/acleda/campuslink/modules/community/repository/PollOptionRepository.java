package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.PollOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PollOptionRepository extends JpaRepository<PollOption, UUID> {

    List<PollOption> findByPollIdInOrderByCreatedAtAsc(Collection<UUID> pollIds);

    /** Guards a vote: the option must belong to the poll being voted on. */
    Optional<PollOption> findByIdAndPollId(UUID id, UUID pollId);

    /**
     * Atomic tally change, so two people voting the same option at once cannot lose an
     * increment (mirrors PostRepository.adjustLikeCount). Clears the context so the
     * follow-up read of the poll returns the fresh counts, not the stale cached rows.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PollOption o SET o.voteCount = o.voteCount + :delta WHERE o.id = :optionId")
    void adjustVoteCount(@Param("optionId") UUID optionId, @Param("delta") int delta);
}
