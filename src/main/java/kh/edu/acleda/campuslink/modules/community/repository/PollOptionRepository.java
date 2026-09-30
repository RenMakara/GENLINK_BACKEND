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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PollOption o WHERE o.poll.id = :pollId")
    void deleteByPollId(@Param("pollId") UUID pollId);

    /** Updates poll totals atomically to avoid lost votes. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PollOption o SET o.voteCount = o.voteCount + :delta WHERE o.id = :optionId")
    void adjustVoteCount(@Param("optionId") UUID optionId, @Param("delta") int delta);
}
