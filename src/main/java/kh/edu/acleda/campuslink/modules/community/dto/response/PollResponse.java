package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

/** A poll rendered for one viewer, embedded in the {@code POLL} post it belongs to. */
public record PollResponse(
    UUID pollId,
    String question,
    int totalVotes,
    boolean hasVoted,
    UUID myOptionId,
    List<Option> options,
    Integer durationDays,
    Instant expiresAt
) {

    public PollResponse(UUID pollId, String question, int totalVotes, boolean hasVoted,
                        UUID myOptionId, List<Option> options) {
        this(pollId, question, totalVotes, hasVoted, myOptionId, options, null, null);
    }

    /** One choice with its live tally. */
    public record Option(
        UUID optionId,
        String text,
        int voteCount
    ) {}
}
