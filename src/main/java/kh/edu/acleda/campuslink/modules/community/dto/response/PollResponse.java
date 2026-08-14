package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * A poll rendered for one viewer, embedded in the {@code POLL} post it belongs to.
 *
 * @param totalVotes  sum of every option's count, so the client can draw percentages
 *                    without a second pass.
 * @param hasVoted    whether the viewer has already voted; {@code myOptionId} names which.
 * @param myOptionId  the option the viewer picked, or null if they have not voted.
 */
public record PollResponse(
    UUID pollId,
    String question,
    int totalVotes,
    boolean hasVoted,
    UUID myOptionId,
    List<Option> options
) {

    /** One choice with its live tally. */
    public record Option(
        UUID optionId,
        String text,
        int voteCount
    ) {}
}
